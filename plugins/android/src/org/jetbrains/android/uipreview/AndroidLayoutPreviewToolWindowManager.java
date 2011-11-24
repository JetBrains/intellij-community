package org.jetbrains.android.uipreview;

import com.android.ide.common.resources.configuration.*;
import com.android.sdklib.IAndroidTarget;
import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.maven.AndroidMavenUtil;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidSdkNotConfiguredException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLayoutPreviewToolWindowManager implements ProjectComponent {
  private static final Icon ANDROID_PREVIEW_ICON = IconLoader.getIcon("/icons/androidPreview.png");

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager");

  private final MergingUpdateQueue myToolWindowUpdateQueue;

  private final MergingUpdateQueue myRenderingQueue;

  private final Project myProject;
  private final FileEditorManager myFileEditorManager;

  private AndroidLayoutPreviewToolWindowForm myToolWindowForm;
  private ToolWindow myToolWindow;
  private boolean myToolWindowReady = false;
  private boolean myToolWindowDisposed = false;
  private final VirtualFileListener myListener = new MyVirtualFileListener();

  private static final Object RENDERING_LOCK = new Object();

  public AndroidLayoutPreviewToolWindowManager(final Project project, final FileEditorManager fileEditorManager) {
    myProject = project;
    myFileEditorManager = fileEditorManager;

    myToolWindowUpdateQueue = new MergingUpdateQueue("android.layout.preview", 300, true, null, project);
    myRenderingQueue = new MergingUpdateQueue("android.layout.rendering", 300, true, null, project, null, false);

    final MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyFileEditorManagerListener());
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new MyAndroidPlatformListener(project));

    LocalFileSystem.getInstance().addVirtualFileListener(myListener);
    Disposer.register(project, new Disposable() {
      public void dispose() {
        LocalFileSystem.getInstance().removeVirtualFileListener(myListener);
      }
    });

    PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
      public void childrenChanged(PsiTreeChangeEvent event) {
        update(event);
      }

      public void childRemoved(PsiTreeChangeEvent event) {
        update(event);
      }

      public void childAdded(PsiTreeChangeEvent event) {
        update(event);
      }

      public void childReplaced(PsiTreeChangeEvent event) {
        update(event);
      }
    }, project);

    CompilerManager.getInstance(project).addAfterTask(new CompileTask() {
      @Override
      public boolean execute(CompileContext context) {
        if (myToolWindowForm != null &&
            myToolWindowReady &&
            !myToolWindowDisposed) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              render();
            }
          });
        }
        return true;
      }
    });
  }

  private void update(PsiTreeChangeEvent event) {
    if (myToolWindowForm != null) {
      final PsiFile file = event.getFile();
      if (file != null && myToolWindowForm.getFile() == file) {
        render();
      }
    }
  }

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        myToolWindowReady = true;
      }
    });
  }

  private void initToolWindow() {
    myToolWindowForm = new AndroidLayoutPreviewToolWindowForm(myProject, this);
    final String toolWindowId = AndroidBundle.message("android.layout.preview.tool.window.title");
    myToolWindow =
      ToolWindowManager.getInstance(myProject).registerToolWindow(toolWindowId, false, ToolWindowAnchor.RIGHT, myProject, true);
    myToolWindow.setIcon(ANDROID_PREVIEW_ICON);

    ((ToolWindowManagerEx)ToolWindowManager.getInstance(myProject)).addToolWindowManagerListener(new ToolWindowManagerAdapter() {
      private boolean myVisible = false;

      @Override
      public void stateChanged() {
        if (myProject.isDisposed()) {
          return;
        }

        final ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(toolWindowId);
        if (window != null && window.isAvailable()) {
          final boolean visible = window.isVisible();
          AndroidLayoutPreviewToolWindowSettings.getInstance(myProject).getState().setVisible(visible);

          if (visible && !myVisible) {
            render();
          }
          myVisible = visible;
        }
      }
    });

    final JPanel contentPanel = myToolWindowForm.getContentPanel();
    final ContentManager contentManager = myToolWindow.getContentManager();
    final Content content = contentManager.getFactory().createContent(contentPanel, null, false);
    content.setDisposer(myToolWindowForm);
    content.setCloseable(false);
    content.setPreferredFocusableComponent(contentPanel);
    contentManager.addContent(content);
    contentManager.setSelectedContent(content, true);
    myToolWindow.setAvailable(false, null);
  }

  public void projectClosed() {
    if (myToolWindowForm != null) {
      Disposer.dispose(myToolWindowForm);
      myToolWindowForm = null;
      myToolWindow = null;
      myToolWindowDisposed = true;
    }
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "AndroidLayoutPreviewToolWindowManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private void processFileEditorChange(final TextEditor newEditor) {
    myToolWindowUpdateQueue.cancelAllUpdates();
    myToolWindowUpdateQueue.queue(new Update("update") {
      public void run() {
        if (!myToolWindowReady || myToolWindowDisposed) {
          return;
        }
        final Editor activeEditor = newEditor != null ? newEditor.getEditor() : null;

        if (myToolWindow == null) {
          if (activeEditor == null) {
            return;
          }
          initToolWindow();
        }

        final AndroidLayoutPreviewToolWindowSettings settings = AndroidLayoutPreviewToolWindowSettings.getInstance(myProject);
        final boolean hideForNonLayoutFiles = settings.getState().isHideForNonLayoutFiles();

        if (activeEditor == null) {
          myToolWindowForm.setFile(null);
          myToolWindow.setAvailable(!hideForNonLayoutFiles, null);
          return;
        }

        final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(activeEditor.getDocument());
        if (psiFile == null) {
          myToolWindowForm.setFile(null);
          myToolWindow.setAvailable(!hideForNonLayoutFiles, null);
          return;
        }

        final boolean toRender = myToolWindowForm.getFile() != psiFile;
        if (toRender) {
          ApplicationManager.getApplication().saveAll();
          myToolWindowForm.setFile(psiFile);
        }

        myToolWindow.setAvailable(true, null);
        final boolean visible = AndroidLayoutPreviewToolWindowSettings.getInstance(myProject).getState().isVisible();
        if (visible) {
          myToolWindow.show(null);
        }
        
        if (toRender) {
          render();
        }
      }
    });
  }

  public void render() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (!myToolWindow.isVisible()) {
      return;
    }
    
    final PsiFile psiFile = myToolWindowForm.getFile();
    if (psiFile == null) {
      return;
    }
    
    final AndroidFacet facet = AndroidFacet.getInstance(psiFile);
    if (facet == null) {
      return;
    }

    myRenderingQueue.queue(new Update("render") {
      @Override
      public void run() {
        ProgressManager.getInstance().runProcess(new Runnable() {
          @Override
          public void run() {
            DumbService.getInstance(myProject).waitForSmartMode();
            doRender(facet, psiFile);
          }
        }, new MyProgressIndicator());
      }

      @Override
      public boolean canEat(Update update) {
        return true;
      }
    });
  }

  private void doRender(@NotNull final AndroidFacet facet, @NotNull final PsiFile psiFile) {
    if (myToolWindowForm == null) {
      return;
    }

    BufferedImage image = null;
    RenderingErrorMessage errorMessage = null;
    String warnMessage = null;

    final String imgPath = FileUtil.getTempDirectory() + "/androidLayoutPreview.png";

    try {
      if (AndroidPlatform.getInstance(facet.getModule()) == null) {
        throw new AndroidSdkNotConfiguredException();
      }

      final LayoutDeviceConfiguration deviceConfiguration = myToolWindowForm.getSelectedDeviceConfiguration();
      if (deviceConfiguration == null) {
        throw new RenderingException("Device is not specified");
      }
      final FolderConfiguration config = new FolderConfiguration();
      config.set(deviceConfiguration.getConfiguration());
      config.setUiModeQualifier(new UiModeQualifier(myToolWindowForm.getSelectedDockMode()));
      config.setNightModeQualifier(new NightModeQualifier(myToolWindowForm.getSelectedNightMode()));

      final LocaleData localeData = myToolWindowForm.getSelectedLocaleData();
      if (localeData == null) {
        throw new RenderingException("Locale is not specified");
      }

      config.setLanguageQualifier(new LanguageQualifier(localeData.getLanguage()));
      config.setRegionQualifier(new RegionQualifier(localeData.getRegion()));

      final IAndroidTarget target = myToolWindowForm.getSelectedTarget();
      final ThemeData theme = myToolWindowForm.getSelectedTheme();

      final float xdpi = deviceConfiguration.getDevice().getXDpi();
      final float ydpi = deviceConfiguration.getDevice().getYDpi();

      final String layoutXmlText = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Override
        public String compute() {
          return psiFile.getText();
        }
      });
      final VirtualFile layoutXmlFile = psiFile.getVirtualFile();

      synchronized (RENDERING_LOCK) {
        final StringBuilder warnBuilder = new StringBuilder();

        if (target != null && theme != null &&
            RenderUtil.renderLayout(myProject, layoutXmlText, layoutXmlFile, imgPath,
                                    target, facet, config, xdpi, ydpi, theme, warnBuilder)) {
          warnMessage = warnBuilder.toString();
          final File input = new File(imgPath);
          image = ImageIO.read(input);
        }
      }
    }
    catch (RenderingException e) {
      LOG.debug(e);
      String message = e.getPresentableMessage();
      message = message != null ? message : AndroidBundle.message("android.layout.preview.default.error.message");
      final Throwable cause = e.getCause();
      errorMessage = cause != null ? new RenderingErrorMessage(message + ' ', "Details", "", new Runnable() {
        @Override
        public void run() {
          showStackStace(cause);
        }
      }) : new RenderingErrorMessage(message);
    }
    catch (IOException e) {
      LOG.info(e);
      final String message = e.getMessage();
      errorMessage = new RenderingErrorMessage("I/O error" + (message != null ? ": " + message : ""));
    }
    catch (AndroidSdkNotConfiguredException e) {
      LOG.debug(e);

      if (!AndroidMavenUtil.isMavenizedModule(facet.getModule())) {
        errorMessage = new RenderingErrorMessage("Please ", "configure", " Android SDK", new Runnable() {
          @Override
          public void run() {
            AndroidSdkUtils.openModuleDependenciesConfigurable(facet.getModule());
          }
        });
      }
      else {
        errorMessage = new RenderingErrorMessage(AndroidBundle.message("android.maven.cannot.parse.android.sdk.error",
                                                                       facet.getModule().getName()));
      }
    }

    final RenderingErrorMessage finalErrorMessage = errorMessage;
    final String finalWarnMessage = warnMessage;
    final BufferedImage finalImage = image;

    if (!myRenderingQueue.isEmpty()) {
      return;
    }

    final String fileName = psiFile.getName();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (!myToolWindowReady || myToolWindowDisposed) {
          return;
        }
        myToolWindowForm.setErrorMessage(finalErrorMessage);
        myToolWindowForm.setWarnMessage(finalWarnMessage);
        if (finalErrorMessage == null) {
          myToolWindowForm.setImage(finalImage, fileName);
        }
        myToolWindowForm.updatePreviewPanel();
      }
    });
  }

  private void showStackStace(@NotNull Throwable t) {
    final String stackTrace = getStackTrace(t);
    final DialogWrapper wrapper = new DialogWrapper(myProject, false) {

      {
        init();
      }

      @Override
      protected JComponent createCenterPanel() {
        final JPanel panel = new JPanel(new BorderLayout());
        final JTextArea textArea = new JTextArea(stackTrace);
        textArea.setEditable(false);
        textArea.setRows(40);
        textArea.setColumns(70);
        panel.add(ScrollPaneFactory.createScrollPane(textArea));
        return panel;
      }
    };
    wrapper.setTitle("Stack trace");
    wrapper.show();
  }

  @NotNull
  private static String getStackTrace(@NotNull Throwable t) {
    final StringWriter stringWriter = new StringWriter();
    final PrintWriter writer = new PrintWriter(stringWriter);
    try {
      t.printStackTrace(writer);
      return stringWriter.toString();
    }
    finally {
      writer.close();
    }
  }

  @Nullable
  private TextEditor getActiveLayoutXmlEditor() {
    FileEditor[] fileEditors = myFileEditorManager.getSelectedEditors();
    if (fileEditors.length > 0 && fileEditors[0] instanceof TextEditor) {
      final TextEditor textEditor = (TextEditor)fileEditors[0];
      if (isApplicableEditor(textEditor)) {
        return textEditor;
      }
    }
    return null;
  }

  private boolean isApplicableEditor(TextEditor textEditor) {
    final Document document = textEditor.getEditor().getDocument();
    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    return psiFile instanceof XmlFile &&
           AndroidFacet.getInstance(psiFile) != null &&
           LayoutDomFileDescription.isLayoutFile((XmlFile)psiFile);
  }

  public static AndroidLayoutPreviewToolWindowManager getInstance(Project project) {
    return project.getComponent(AndroidLayoutPreviewToolWindowManager.class);
  }

  private class MyVirtualFileListener extends VirtualFileAdapter {
    @Override
    public void propertyChanged(VirtualFilePropertyEvent event) {
      fileChanged(event);
    }

    @Override
    public void fileCreated(VirtualFileEvent event) {
      fileChanged(event);
    }

    @Override
    public void fileDeleted(VirtualFileEvent event) {
      fileChanged(event);
    }

    @Override
    public void fileMoved(VirtualFileMoveEvent event) {
      fileChanged(event);
    }

    private void fileChanged(VirtualFileEvent event) {
      if (myToolWindowForm == null || !myToolWindowReady || myToolWindowDisposed) {
        return;
      }

      final PsiFile layoutFile = myToolWindowForm.getFile();
      if (layoutFile == null) {
        return;
      }

      final VirtualFile changedFile = event.getFile();
      final VirtualFile parent = changedFile.getParent();

      if (parent == null) {
        return;
      }

      if (ResourceManager.isResourceDirectory(parent, myProject)) {
        myToolWindowForm.updateLocales();
        render();
      }

      final VirtualFile gp = parent.getParent();
      if (gp != null && ResourceManager.isResourceDirectory(gp, myProject)) {
        myToolWindowForm.updateLocales();
        render();
      }
    }
  }

  private class MyAndroidPlatformListener implements ModuleRootListener {
    private final Map<Module, Sdk> myModule2Sdk = new HashMap<Module, Sdk>();
    private final Project myProject;

    private MyAndroidPlatformListener(@NotNull Project project) {
      myProject = project;
      updateMap();
    }

    @Override
    public void beforeRootsChange(ModuleRootEvent event) {
    }

    @Override
    public void rootsChanged(ModuleRootEvent event) {
      if (myToolWindowForm == null || !myToolWindowReady || myToolWindowDisposed) {
        return;
      }

      final PsiFile file = myToolWindowForm.getFile();
      if (file != null) {
        final Module module = ModuleUtil.findModuleForPsiElement(file);
        if (module != null) {
          final Sdk prevSdk = myModule2Sdk.get(module);
          final Sdk newSdk = ModuleRootManager.getInstance(module).getSdk();
          if (newSdk != null
              && (newSdk.getSdkType() instanceof AndroidSdkType ||
                  (prevSdk != null && prevSdk.getSdkType() instanceof AndroidSdkType))
              && !newSdk.equals(prevSdk)) {

            final AndroidSdkAdditionalData additionalData = (AndroidSdkAdditionalData)newSdk.getSdkAdditionalData();
            final AndroidPlatform newPlatform = additionalData != null ? additionalData.getAndroidPlatform() : null;
            myToolWindowForm.updateDevicesAndTargets(newPlatform);
            myToolWindowForm.updateThemes();

            render();
          }
        }
      }

      updateMap();
    }

    private void updateMap() {
      myModule2Sdk.clear();
      for (Module module : ModuleManager.getInstance(myProject).getModules()) {
        myModule2Sdk.put(module, ModuleRootManager.getInstance(module).getSdk());
      }
    }
  }

  private class MyFileEditorManagerListener implements FileEditorManagerListener {
    public void fileOpened(FileEditorManager source, VirtualFile file) {
      processFileEditorChange(getActiveLayoutXmlEditor());
    }

    public void fileClosed(FileEditorManager source, VirtualFile file) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          processFileEditorChange(getActiveLayoutXmlEditor());
        }
      });
    }

    public void selectionChanged(FileEditorManagerEvent event) {
      final FileEditor newEditor = event.getNewEditor();
      TextEditor layoutXmlEditor = null;
      if (newEditor instanceof TextEditor) {
        final TextEditor textEditor = (TextEditor)newEditor;
        if (isApplicableEditor(textEditor)) {
          layoutXmlEditor = textEditor;
        }
      }
      processFileEditorChange(layoutXmlEditor);
    }
  }

  private class MyProgressIndicator extends ProgressIndicatorBase {
    private final Object myLock = new Object();

    @Override
    public void start() {
      super.start();
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          final Timer timer = UIUtil.createNamedTimer("Android rendering progress timer", 1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
              synchronized (myLock) {
                if (isRunning() && myToolWindowForm != null) {
                  myToolWindowForm.getPreviewPanel().showProgress();
                }
              }
            }
          });
          timer.setRepeats(false);
          timer.start();
        }
      });
    }

    @Override
    public void stop() {
      synchronized (myLock) {
        super.stop();
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (myToolWindowForm != null) {
              myToolWindowForm.getPreviewPanel().hideProgress();
            }
          }
        });
      }
    }
  }
}
