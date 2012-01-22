package org.jetbrains.android.uipreview;

import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.resources.*;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.io.*;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.manifest.Application;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xmlpull.v1.XmlPullParserException;

import javax.imageio.ImageIO;
import java.io.*;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
class RenderUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.RenderUtil");

  private static final String DEFAULT_APP_LABEL = "Android application";

  private RenderUtil() {
  }

  public static boolean renderLayout(@NotNull Project project,
                                     @NotNull String layoutXmlText,
                                     @Nullable VirtualFile layoutXmlFile,
                                     @NotNull String imgPath,
                                     @NotNull IAndroidTarget target,
                                     @NotNull AndroidFacet facet,
                                     @NotNull FolderConfiguration config,
                                     float xdpi,
                                     float ydpi,
                                     @NotNull ThemeData theme,
                                     StringBuilder warningBuilder)
    throws RenderingException, IOException, AndroidSdkNotConfiguredException {
    final Sdk sdk = ModuleRootManager.getInstance(facet.getModule()).getSdk();
    if (sdk == null || !(sdk.getSdkType() instanceof AndroidSdkType)) {
      throw new AndroidSdkNotConfiguredException();
    }

    final AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
    if (data == null) {
      throw new AndroidSdkNotConfiguredException();
    }

    final AndroidPlatform platform = data.getAndroidPlatform();
    if (platform == null) {
      throw new AndroidSdkNotConfiguredException();
    }

    config.setVersionQualifier(new VersionQualifier(target.getVersion().getApiLevel()));

    final RenderServiceFactory factory = platform.getSdk().getTargetData(target).getRenderServiceFactory(project);
    if (factory == null) {
      throw new RenderingException(AndroidBundle.message("android.layout.preview.cannot.load.library.error"));
    }

    final ProjectResources projectResources = new ProjectResources();

    final VirtualFile[] resourceDirs = facet.getLocalResourceManager().getAllResourceDirs();
    final IAbstractFolder[] resFolders = toAbstractFolders(project, resourceDirs);

    loadResources(projectResources, layoutXmlText, layoutXmlFile, resFolders);
    final int minSdkVersion = getMinSdkVersion(facet);
    String missingRClassMessage = null;
    boolean missingRClass = false;

    final ProjectCallback callback = new ProjectCallback(factory.getLibrary(), facet.getModule(), projectResources);
    try {
      callback.loadAndParseRClass();
    }
    catch (ClassNotFoundException e) {
      LOG.debug(e);
      missingRClassMessage = e.getMessage();
      missingRClass = true;
    }

    final RenderResources resolver =
      factory.createResourceResolver(facet, config, projectResources, theme.getName(), theme.isProjectTheme());
    final RenderService renderService = factory.createService(resolver, config, xdpi, ydpi, callback, minSdkVersion);

    final RenderSession session;
    try {
      session = renderService.createRenderSession(layoutXmlText, getAppLabelToShow(facet));
    }
    catch (XmlPullParserException e) {
      throw new RenderingException(e);
    }
    if (session == null) {
      return false;
    }

    if (missingRClass && callback.hasLoadedClasses()) {
      warningBuilder.append(missingRClassMessage != null && missingRClassMessage.length() > 0
                            ? ("Class not found error: " + missingRClassMessage + ".")
                            : "R class not found.")
        .append(" Try to build project\n");
    }

    final Set<String> missingClasses = callback.getMissingClasses();
    if (missingClasses.size() > 0) {
      if (missingClasses.size() > 1) {
        warningBuilder.append("Missing classes:\n");
        for (String missingClass : missingClasses) {
          warningBuilder.append("&nbsp; &nbsp; &nbsp; &nbsp;").append(missingClass).append('\n');
        }
      }
      else {
        warningBuilder.append("Missing class ").append(missingClasses.iterator().next()).append('\n');
      }
    }

    final Map<String, Throwable> brokenClasses = callback.getBrokenClasses();
    if (brokenClasses.size() > 0) {
      if (brokenClasses.size() > 1) {
        warningBuilder.append("Unable to initialize:\n");
        for (String brokenClass : brokenClasses.keySet()) {
          warningBuilder.append("    ").append(brokenClass).append('\n');
        }
      }
      else {
        warningBuilder.append("Unable to initialize ").append(brokenClasses.keySet().iterator().next());
      }
    }

    if (warningBuilder.length() > 0 && warningBuilder.charAt(warningBuilder.length() - 1) == '\n') {
      warningBuilder.deleteCharAt(warningBuilder.length() - 1);
    }

    final Result result = session.getResult();
    if (!result.isSuccess()) {
      final Throwable exception = result.getException();

      if (exception != null) {
        final List<Throwable> exceptionsFromWarnings = getNonNullValues(brokenClasses);

        if (exceptionsFromWarnings.size() > 0 &&
            exception instanceof ClassCastException &&
            (SdkConstants.CLASS_MOCK_VIEW + " cannot be cast to " + SdkConstants.CLASS_VIEWGROUP)
              .equalsIgnoreCase(exception.getMessage())) {
          throw new RenderingException(exceptionsFromWarnings.toArray(new Throwable[exceptionsFromWarnings.size()]));
        }
        throw new RenderingException(exception);
      }
      final String message = result.getErrorMessage();
      if (message != null) {
        LOG.info(message);
        throw new RenderingException();
      }
      return false;
    }

    final String format = FileUtil.getExtension(imgPath);
    ImageIO.write(session.getImage(), format, new File(imgPath));

    return true;
  }

  @NotNull
  private static <T> List<T> getNonNullValues(@NotNull Map<?, T> map) {
    final List<T> result = new ArrayList<T>();

    for (Map.Entry<?, T> entry : map.entrySet()) {
      final T value = entry.getValue();
      if (value != null) {
        result.add(value);
      }
    }
    return result;
  }

  private static String getAppLabelToShow(final AndroidFacet facet) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        final Manifest manifest = facet.getManifest();
        if (manifest != null) {
          final Application application = manifest.getApplication();
          if (application != null) {
            final String label = application.getLabel().getStringValue();
            if (label != null) {
              return label;
            }
          }
        }
        return DEFAULT_APP_LABEL;
      }
    });
  }

  private static int getMinSdkVersion(final AndroidFacet facet) {
    final XmlTag manifestTag = ApplicationManager.getApplication().runReadAction(new Computable<XmlTag>() {
      @Override
      public XmlTag compute() {
        final Manifest manifest = facet.getManifest();
        return manifest != null ? manifest.getXmlTag() : null;
      }
    });
    if (manifestTag != null) {
      for (XmlTag usesSdkTag : manifestTag.findSubTags("uses-sdk")) {
        final int candidate = AndroidUtils.getIntAttrValue(usesSdkTag, "minSdkVersion");
        if (candidate >= 0) {
          return candidate;
        }
      }
    }
    return -1;
  }

  @NotNull
  private static IAbstractFolder[] toAbstractFolders(@NotNull Project project, @NotNull VirtualFile[] folders) {
    final IAbstractFolder[] result = new IAbstractFolder[folders.length];

    for (int i = 0; i < folders.length; i++) {
      final VirtualFile folder = folders[i];
      final String folderPath = FileUtil.toSystemDependentName(folder.getPath());
      result[i] = new BufferingFolderWrapper(new File(folderPath));
    }
    return result;
  }

  public static void loadResources(@NotNull ResourceRepository repository,
                                   @Nullable final String layoutXmlFileText,
                                   @Nullable VirtualFile layoutXmlFile,
                                   @NotNull IAbstractFolder... rootFolders) throws IOException, RenderingException {
    final ScanningContext scanningContext = new ScanningContext(repository);

    for (IAbstractFolder rootFolder : rootFolders) {
      for (IAbstractResource file : rootFolder.listMembers()) {
        if (!(file instanceof IAbstractFolder)) {
          continue;
        }

        final IAbstractFolder folder = (IAbstractFolder)file;
        final ResourceFolder resFolder = repository.processFolder(folder);

        if (resFolder != null) {
          for (final IAbstractResource childRes : folder.listMembers()) {
            
            if (childRes instanceof IAbstractFile) {
              final VirtualFile vFile;

              if (childRes instanceof BufferingFileWrapper) { 
                final BufferingFileWrapper fileWrapper = (BufferingFileWrapper)childRes;
                final String filePath = FileUtil.toSystemIndependentName(fileWrapper.getOsLocation());
                vFile = LocalFileSystem.getInstance().findFileByPath(filePath);
  
                if (vFile != null && vFile == layoutXmlFile && layoutXmlFileText != null) {
                  resFolder.processFile(new MyFileWrapper(layoutXmlFileText, childRes), ResourceDeltaKind.ADDED, scanningContext);
                }
                else {
                  resFolder.processFile((IAbstractFile)childRes, ResourceDeltaKind.ADDED, scanningContext);
                }
              }
              else {
                LOG.error("childRes must be instance of " + BufferingFileWrapper.class.getName());
              }
            }
          }
        }
      }
    }

    final List<String> errors = scanningContext.getErrors();
    if (errors != null && errors.size() > 0) {
      LOG.debug(new RenderingException(merge(errors)));
    }
  }

  private static String merge(@NotNull Collection<String> strs) {
    final StringBuilder result = new StringBuilder();
    for (Iterator<String> it = strs.iterator(); it.hasNext(); ) {
      String str = it.next();
      result.append(str);
      if (it.hasNext()) {
        result.append('\n');
      }
    }
    return result.toString();
  }

  private static class MyFileWrapper implements IAbstractFile {
    private final String myLayoutXmlFileText;
    private final IAbstractResource myChildRes;

    public MyFileWrapper(String layoutXmlFileText, IAbstractResource childRes) {
      myLayoutXmlFileText = layoutXmlFileText;
      myChildRes = childRes;
    }

    @Override
    public InputStream getContents() throws StreamException {
      return new ByteArrayInputStream(myLayoutXmlFileText.getBytes());
    }

    @Override
    public void setContents(InputStream source) throws StreamException {
      throw new UnsupportedOperationException();
    }

    @Override
    public OutputStream getOutputStream() throws StreamException {
      throw new UnsupportedOperationException();
    }

    @Override
    public PreferredWriteMode getPreferredWriteMode() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getModificationStamp() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
      return myChildRes.getName();
    }

    @Override
    public String getOsLocation() {
      return myChildRes.getOsLocation();
    }

    @Override
    public boolean exists() {
      return true;
    }

    @Override
    public IAbstractFolder getParentFolder() {
      return myChildRes.getParentFolder();
    }

    @Override
    public boolean delete() {
      throw new UnsupportedOperationException();
    }
  }
}
