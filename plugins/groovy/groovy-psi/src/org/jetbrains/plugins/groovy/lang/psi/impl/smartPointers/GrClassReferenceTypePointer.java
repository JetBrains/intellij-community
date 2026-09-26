// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.smartPointers;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.SmartTypePointer;
import com.intellij.psi.impl.smartPointers.TypePointerBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClassReferenceType;

public class GrClassReferenceTypePointer extends TypePointerBase<GrClassReferenceType> implements SmartTypePointer {
  private static final Logger LOG = Logger.getInstance(GrClassReferenceTypePointer.class);

  private final SmartPsiElementPointer<GrCodeReferenceElement> mySmartPsiElementPointer;
  private final String myReferenceText;
  private final Project myProject;

  public GrClassReferenceTypePointer(GrClassReferenceType type, Project project) {
    super(type);

    myProject = project;
    final GrCodeReferenceElement reference = type.getReference();
    mySmartPsiElementPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(reference);
    myReferenceText = reference.getText();
  }

  @Override
  protected @Nullable GrClassReferenceType calcType() {
    final GrCodeReferenceElement reference = mySmartPsiElementPointer.getElement();
    if (reference != null) {
      return new GrClassReferenceType(reference);
    }

    try {
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myProject);
      GrTypeElement typeElement = factory.createTypeElement(myReferenceText, null);
      return (GrClassReferenceType)typeElement.getType();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    return null;

  }
}
