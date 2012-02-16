package org.jetbrains.jps.android

import org.jetbrains.jps.MacroExpander
import org.jetbrains.jps.Module
import org.jetbrains.jps.idea.Facet
import org.jetbrains.jps.idea.FacetTypeService

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

    facetConfiguration.each {Node child ->
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
      }
    }
    return facet;
  }
}
