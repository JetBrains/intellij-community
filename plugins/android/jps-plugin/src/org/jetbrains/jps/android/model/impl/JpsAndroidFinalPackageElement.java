package org.jetbrains.jps.android.model.impl;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsModuleReference;

/**
 * @author Eugene.Kudelevsky
 */
public class JpsAndroidFinalPackageElement extends JpsCompositeElementBase<JpsAndroidFinalPackageElement> implements JpsPackagingElement {
  private static final JpsElementChildRole<JpsModuleReference>
    MODULE_REFERENCE_CHILD_ROLE = JpsElementChildRoleBase.create("module reference");

  public JpsAndroidFinalPackageElement(JpsModuleReference moduleReference) {
    myContainer.setChild(MODULE_REFERENCE_CHILD_ROLE, moduleReference);
  }

  public JpsAndroidFinalPackageElement(JpsAndroidFinalPackageElement original) {
    super(original);
  }

  @NotNull
  @Override
  public JpsAndroidFinalPackageElement createCopy() {
    return new JpsAndroidFinalPackageElement(this);
  }

  public JpsModuleReference getModuleReference() {
    return myContainer.getChild(MODULE_REFERENCE_CHILD_ROLE);
  }
}
