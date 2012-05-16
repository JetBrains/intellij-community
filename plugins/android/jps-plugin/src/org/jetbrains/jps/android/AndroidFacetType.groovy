package org.jetbrains.jps.android

import org.jetbrains.android.util.AndroidCommonUtils
import org.jetbrains.android.util.AndroidNativeLibData
import org.jetbrains.jps.MacroExpander
import org.jetbrains.jps.Module
import org.jetbrains.jps.idea.Facet
import org.jetbrains.jps.idea.FacetTypeService
import org.jetbrains.jps.idea.IdeaProjectLoadingUtil

/**
 * @author Eugene.Kudelevsky
 */
class AndroidFacetType extends FacetTypeService {
  public static final String ID = "android"

  AndroidFacetType() {
    super(ID)
  }

  @Override
  Facet createFacet(Module module, String name, Node facetConfiguration, MacroExpander macroExpander) {
    def facet = new AndroidFacet(module, name);
    facet.additionalNativeLibs = new ArrayList<AndroidNativeLibData>()

    facetConfiguration.each {Node child ->
      if (AndroidCommonUtils.INCLUDE_SYSTEM_PROGUARD_FILE_ELEMENT_NAME.equals(child.name())) {
        facet.includeSystemProguardCfgFile = Boolean.parseBoolean((String)child.text())
      }

      if (AndroidCommonUtils.ADDITIONAL_NATIVE_LIBS_ELEMENT.equals(child.name())) {
        child.each {Node nativeLibItem ->
          final architecture = nativeLibItem.get("@" + AndroidCommonUtils.ARCHITECTURE_ATTRIBUTE)
          final url = nativeLibItem.get("@" + AndroidCommonUtils.URL_ATTRIBUTE)
          final targetFileName = nativeLibItem.get("@" + AndroidCommonUtils.TARGET_FILE_NAME_ATTRIBUTE)

          if (architecture != null && url != null && targetFileName != null) {
            final path = macroExpander.expandMacros(IdeaProjectLoadingUtil.pathFromUrl((String)url))
            facet.additionalNativeLibs.add(new AndroidNativeLibData((String)architecture, path, (String)targetFileName))
          }
        }
      }

      String value = child."@value"

      switch (child."@name") {
        case "RES_FOLDER_RELATIVE_PATH":
          facet.resFolderRelativePath = value
          break
        case "USE_CUSTOM_APK_RESOURCE_FOLDER":
          facet.useCustomResFolderForCompilation = Boolean.parseBoolean(value)
          break
        case "CUSTOM_APK_RESOURCE_FOLDER":
          facet.resFolderForCompilationRelativePath = value
          break
        case "LIBRARY_PROJECT":
          facet.library = Boolean.parseBoolean(value)
          break
        case "MANIFEST_FILE_RELATIVE_PATH":
          facet.manifestRelativePath = value
          break
        case "USE_CUSTOM_COMPILER_MANIFEST":
          facet.useCustomManifestForCompilation = Boolean.parseBoolean(value)
          break
        case "CUSTOM_COMPILER_MANIFEST":
          facet.manifestForCompilationRelativePath = value
          break
        case "PACK_TEST_CODE":
          facet.packTestCode = Boolean.parseBoolean(value)
          break
        case "ASSETS_FOLDER_RELATIVE_PATH":
          facet.assetsFolderRelativePath = value
          break
        case "APK_PATH":
          facet.apkRelativePath = value
          break
        case "CUSTOM_DEBUG_KEYSTORE_PATH":
          facet.customDebugKeyStorePath = macroExpander.expandMacros(IdeaProjectLoadingUtil.pathFromUrl(value))
          break
        case "LIBS_FOLDER_RELATIVE_PATH":
          facet.nativeLibsFolderRelativePath = value
          break
        case "RUN_PROCESS_RESOURCES_MAVEN_TASK":
          facet.runProcessResourcesMavenTask = Boolean.parseBoolean(value)
          break
        case "RUN_PROGUARD":
          facet.runProguard = Boolean.parseBoolean(value)
          break
        case "PROGUARD_CFG_PATH":
          facet.proguardConfigFileRelativePath = value
          break
      }
    }
    return facet;
  }
}
