package org.jetbrains.android.dom.manifest;

import com.android.sdklib.SdkConstants;
import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class ManifestDomFileDescription extends DomFileDescription<Manifest> {
  public ManifestDomFileDescription() {
    super(Manifest.class, "manifest");
  }

  public boolean isMyFile(@NotNull XmlFile file, @Nullable Module module) {
    return isManifestFile(file);
  }

  public static boolean isManifestFile(@NotNull XmlFile file) {
    if (!file.getName().equals(SdkConstants.FN_ANDROID_MANIFEST_XML)) {
      return false;
    }
    return AndroidFacet.getInstance(file) != null;
  }

  protected void initializeFileDescription() {
    registerNamespacePolicy(AndroidUtils.NAMESPACE_KEY, SdkConstants.NS_RESOURCES);
  }
}
