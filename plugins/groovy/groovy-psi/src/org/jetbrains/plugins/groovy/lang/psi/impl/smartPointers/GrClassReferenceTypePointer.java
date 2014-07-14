/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.psi.impl.smartPointers;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.SmartTypePointer;
import com.intellij.psi.impl.smartPointers.TypePointerBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClassReferenceType;

/**
 * Created by Max Medvedev on 10/25/13
 */
public class GrClassReferenceTypePointer extends TypePointerBase<GrClassReferenceType> implements SmartTypePointer {
  private static final Logger LOG = Logger.getInstance(GrClassReferenceTypePointer.class);

  private final SmartPsiElementPointer<GrReferenceElement> mySmartPsiElementPointer;
  private final String myReferenceText;
  private final Project myProject;

  public GrClassReferenceTypePointer(GrClassReferenceType type, Project project) {
    super(type);

    myProject = project;
    final GrReferenceElement reference = type.getReference();
    mySmartPsiElementPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(reference);
    myReferenceText = reference.getText();
  }

  @Nullable
  @Override
  protected GrClassReferenceType calcType() {
    final GrReferenceElement reference = mySmartPsiElementPointer.getElement();
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
