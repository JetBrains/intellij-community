package org.jetbrains.android.uipreview;

import com.android.ide.common.resources.configuration.*;
import com.android.sdklib.IAndroidTarget;
import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.sdk.*;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidSdkNotConfiguredException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLayoutPreviewToolWindowManager implements ProjectComponent {
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
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new MyAndroidPlatformListener());

    LocalFileSystem.getInstance().addVirtualFileListener(myListener);

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
    myToolWindowForm = new AndroidLayoutPreviewToolWindowForm(this);
    myToolWindow = ToolWindowManager.getInstance(myProject)
      .registerToolWindow(AndroidBundle.message("android.layout.preview.tool.window.title"), false, ToolWindowAnchor.RIGHT, myProject,
                          true);
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
    LocalFileSystem.getInstance().removeVirtualFileListener(myListener);
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

        if (activeEditor == null) {
          myToolWindowForm.setFile(null);
          myToolWindow.setAvailable(false, null);
          return;
        }

        final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(activeEditor.getDocument());
        if (psiFile == null) {
          myToolWindowForm.setFile(null);
          myToolWindow.setAvailable(false, null);
          return;
        }

        if (myToolWindowForm.getFile() != psiFile) {
          ApplicationManager.getApplication().saveAll();
          myToolWindowForm.setFile(psiFile);
          render();
        }

        myToolWindow.setAvailable(true, null);
        myToolWindow.show(null);
      }
    });
  }

  public void render() {
    final PsiFile psiFile = myToolWindowForm.getFile();
    if (psiFile == null) {
      return;
    }

    final String layoutXmlText = psiFile.getText();
    final AndroidFacet facet = AndroidFacet.getInstance(psiFile);
    assert facet != null;

    myRenderingQueue.queue(new Update("render") {
      @Override
      public void run() {
        doRender(facet, layoutXmlText);
      }

      @Override
      public boolean canEat(Update update) {
        return true;
      }
    });
  }

  private void doRender(final AndroidFacet facet, String layoutXmlText) {
    BufferedImage image = null;
    RenderingErrorMessage errorMessage = null;

    final String imgPath = FileUtil.getTempDirectory() + "/androidLayoutPreview.png";

    try {
      final LayoutDeviceConfiguration deviceConfiguration = myToolWindowForm.getSelectedDeviceConfiguration();
      if (deviceConfiguration == null) {
        throw new RenderingException("Device is not specified");
      }
      final FolderConfiguration config = new FolderConfiguration();
      config.set(deviceConfiguration.getConfiguration());
      config.setDockModeQualifier(new DockModeQualifier(myToolWindowForm.getSelectedDockMode()));
      config.setNightModeQualifier(new NightModeQualifier(myToolWindowForm.getSelectedNightMode()));

      final LocaleData localeData = myToolWindowForm.getSelectedLocaleData();

      config.setLanguageQualifier(new LanguageQualifier(localeData.getLanguage()));
      config.setRegionQualifier(new RegionQualifier(localeData.getRegion()));

      final IAndroidTarget target = myToolWindowForm.getSelectedTarget();
      final ThemeData theme = myToolWindowForm.getSelectedTheme();

      synchronized (RENDERING_LOCK) {
        if (target != null && theme != null &&
            RenderUtil.renderLayout(myProject, layoutXmlText, imgPath, target, facet, config, theme)) {
          final File input = new File(imgPath);
          image = ImageIO.read(input);
        }
      }
    }
    catch (RenderingException e) {
      LOG.debug(e);
      final String message = e.getPresentableMessage();
      errorMessage = new RenderingErrorMessage(message != null
                                        ? message
                                        : AndroidBundle.message("android.layout.preview.default.error.message"));
    }
    catch (IOException e) {
      LOG.info(e);
      final String message = e.getMessage();
      errorMessage = new RenderingErrorMessage("I/O error" + (message != null ? ": " + message : ""));
    }
    catch (AndroidSdkNotConfiguredException e) {
      LOG.debug(e);
      errorMessage = new RenderingErrorMessage("Please ", "configure", " Android SDK", new Runnable() {
        @Override
        public void run() {
          AndroidSdkUtils.openModuleDependenciesConfigurable(facet.getModule());
        }
      });
    }

    final RenderingErrorMessage finalErrorMessage = errorMessage;
    final BufferedImage finalImage = image;

    if (!myRenderingQueue.isEmpty()) {
      return;
    }

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (!myToolWindowReady || myToolWindowDisposed) {
          return;
        }
        myToolWindowForm.setErrorMessage(finalErrorMessage);
        if (finalErrorMessage == null) {
          myToolWindowForm.setImage(finalImage);
        }
        myToolWindowForm.updatePreviewPanel();
      }
    });
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

      if (parent != null && ResourceManager.isResourceDirectory(parent, myProject)) {
        myToolWindowForm.updateLocales();
        render();
      }
    }
  }

  private class MyAndroidPlatformListener implements ModuleRootListener {
    private final Map<Module, Sdk> myModule2Sdk = new HashMap<Module, Sdk>();

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
          if (newSdk != null &&
              (newSdk.getSdkType() instanceof AndroidSdkType || prevSdk.getSdkType() instanceof AndroidSdkType) &&
              !newSdk.equals(prevSdk)) {
            myModule2Sdk.put(module, newSdk);

            final AndroidSdkAdditionalData additionalData = (AndroidSdkAdditionalData)newSdk.getSdkAdditionalData();
            final AndroidPlatform newPlatform = additionalData != null ? additionalData.getAndroidPlatform() : null;
            myToolWindowForm.updateDevicesAndTargets(newPlatform);
            myToolWindowForm.updateThemes();

            render();
          }
        }
      }
    }
  }

  private class MyFileEditorManagerListener implements FileEditorManagerListener {
    public void fileOpened(FileEditorManager source, VirtualFile file) {
      processFileEditorChange(getActiveLayoutXmlEditor());
    }

    public void fileClosed(FileEditorManager source, VirtualFile file) {
      processFileEditorChange(getActiveLayoutXmlEditor());
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
}
