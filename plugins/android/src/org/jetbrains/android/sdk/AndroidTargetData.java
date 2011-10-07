package org.jetbrains.android.sdk;

import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.resourceManagers.SystemResourceManager;
import org.jetbrains.android.uipreview.RenderServiceFactory;
import org.jetbrains.android.uipreview.RenderingException;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidTargetData {
  private final AndroidSdk mySdk;
  private final IAndroidTarget myTarget;

  private volatile AttributeDefinitions myAttrDefs;
  private volatile RenderServiceFactory myRenderServiceFactory;
  private volatile Set<String> myThemes;

  public AndroidTargetData(@NotNull AndroidSdk sdk, @NotNull IAndroidTarget target) {
    mySdk = sdk;
    myTarget = target;
  }

  @Nullable
  public AttributeDefinitions getAttrDefs(@NotNull final Project project) {
    if (myAttrDefs == null) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          final String attrsPath = myTarget.getPath(IAndroidTarget.ATTRIBUTES);
          final String attrsManifestPath = myTarget.getPath(IAndroidTarget.MANIFEST_ATTRIBUTES);
          final XmlFile[] files = AndroidUtils.findXmlFiles(project, attrsPath, attrsManifestPath);
          if (files != null) {
            myAttrDefs = new AttributeDefinitions(files);
          }
        }
      });
    }
    return myAttrDefs;
  }

  @Nullable
  public RenderServiceFactory getRenderServiceFactory(@NotNull Project project) throws RenderingException, IOException {
    if (myRenderServiceFactory == null) {
      final AttributeDefinitions attrDefs = getAttrDefs(project);
      if (attrDefs == null) {
        return null;
      }
      myRenderServiceFactory = RenderServiceFactory.create(myTarget, attrDefs.getEnumMap());
    }
    return myRenderServiceFactory;
  }

  public boolean areThemesCached() {
    return myThemes != null;
  }

  @NotNull
  public Set<String> getThemes(@NotNull final Module module) {
    if (myThemes == null) {
      myThemes = new HashSet<String>();
      final SystemResourceManager systemResourceManager = new SystemResourceManager(module, new AndroidPlatform(mySdk, myTarget));
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          if (module.isDisposed() || module.getProject().isDisposed()) {
            return;
          }

          final List<ResourceElement> styles = systemResourceManager.getValueResources(ResourceType.STYLE.getName());
          for (ResourceElement style : styles) {
            final String styleName = style.getName().getValue();
            if (styleName != null && (styleName.equals("Theme") || styleName.startsWith("Theme."))) {
              myThemes.add(styleName);
            }
          }
        }
      });
    }
    return myThemes;
  }

  @NotNull
  public IAndroidTarget getTarget() {
    return myTarget;
  }
}
