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
    return new AndroidFacet(module, name, "");
  }
}
