package org.jetbrains.android.uipreview;

import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.resources.*;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.io.FolderWrapper;
import com.android.io.IAbstractFile;
import com.android.io.IAbstractFolder;
import com.android.io.IAbstractResource;
import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.dom.manifest.Application;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidSdkNotConfiguredException;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.xmlpull.v1.XmlPullParserException;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
class RenderUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.RenderUtil");

  private static final Map<String, RenderServiceFactory> ourCachedFactories = new HashMap<String, RenderServiceFactory>();
  private static final String DEFAULT_APP_LABEL = "Android application";

  private RenderUtil() {
  }

  public static boolean renderLayout(@NotNull Project project,
                                     @NotNull String layoutXmlText,
                                     @NotNull String imgPath,
                                     @NotNull IAndroidTarget target,
                                     @NotNull AndroidFacet facet,
                                     @NotNull FolderConfiguration config,
                                     @NotNull ThemeData theme)
    throws RenderingException, IOException, AndroidSdkNotConfiguredException {
    final Sdk sdk = ModuleRootManager.getInstance(facet.getModule()).getSdk();
    if (sdk == null || !(sdk.getSdkType() instanceof AndroidSdkType)) {
      throw new AndroidSdkNotConfiguredException();
    }

    final AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
    if (data == null) {
      throw new RenderingException(AndroidBundle.message("sdk.broken.error"));
    }

    final AndroidPlatform platform = data.getAndroidPlatform();
    if (platform == null) {
      throw new RenderingException(AndroidBundle.message("sdk.broken.error"));
    }

    config.setVersionQualifier(new VersionQualifier(target.getVersion().getApiLevel()));

    final RenderServiceFactory factory = platform.getSdk().getTargetData(target).getRenderServiceFactory(project);
    if (factory == null) {
      throw new RenderingException(AndroidBundle.message("android.layout.preview.cannot.load.library.error"));
    }

    /*if (ourTargetHashString == null || ourCachedFactory == null || !ourTargetHashString.equals(target.hashString())) {
      ourTargetHashString = target.hashString();
      ourCachedFactory = RenderServiceFactory.create(target, enumMap);
      if (ourCachedFactory == null) {
        throw new RenderingException(AndroidBundle.message("android.layout.preview.cannot.load.library.error"));
      }
    }*/

    final ResourceRepository repository = new ResourceRepository(false) {
      @Override
      protected ResourceItem createResourceItem(String name) {
        return new ResourceItem(name);
      }
    };

    final VirtualFile[] resourceDirs = facet.getLocalResourceManager().getAllResourceDirs();
    final IAbstractFolder[] resFolders = toAbstractFolders(resourceDirs);

    loadResources(repository, resFolders);

    final ResourceResolver resources = factory.createResourceResolver(config, repository, theme.getName(), theme.isProjectTheme());
    final int minSdkVersion = getMinSdkVersion(facet);
    final RenderService renderService = factory.createService(resources, config, new ProjectCallback(), minSdkVersion);

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

    final Result result = session.getResult();
    if (!result.isSuccess()) {
      final Throwable exception = result.getException();
      if (exception != null) {
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
  private static IAbstractFolder[] toAbstractFolders(@NotNull VirtualFile[] folders) {
    final IAbstractFolder[] result = new IAbstractFolder[folders.length];
    for (int i = 0; i < folders.length; i++) {
      result[i] = new FolderWrapper(folders[i].getPath());
    }
    return result;
  }

  public static void loadResources(@NotNull ResourceRepository repository, @NotNull IAbstractFolder... rootFolders) throws IOException {
    for (IAbstractFolder rootFolder : rootFolders) {
      for (IAbstractResource file : rootFolder.listMembers()) {
        if (!(file instanceof IAbstractFolder)) {
          continue;
        }

        final IAbstractFolder folder = (IAbstractFolder)file;
        final ResourceFolder resFolder = repository.processFolder(folder);

        if (resFolder != null) {
          for (IAbstractResource childRes : folder.listMembers()) {
            if (childRes instanceof IAbstractFile) {
              resFolder.processFile((IAbstractFile)childRes, ResourceDeltaKind.ADDED);
            }
          }
        }
      }
    }
  }
}
