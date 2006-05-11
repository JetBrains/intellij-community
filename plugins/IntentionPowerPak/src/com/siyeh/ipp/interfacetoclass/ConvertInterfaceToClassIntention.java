/*
 * Copyright 2006 Bas Leijdekkers
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
package com.siyeh.ipp.interfacetoclass;

import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConvertInterfaceToClassIntention extends Intention {

	private static void changeInterfaceToClass(PsiClass anInterface)
			throws IncorrectOperationException {
		final PsiIdentifier nameIdentifier = anInterface.getNameIdentifier();
		assert nameIdentifier != null;
		final PsiElement whiteSpace = nameIdentifier.getPrevSibling();
		assert whiteSpace != null;
		final PsiElement interfaceToken = whiteSpace.getPrevSibling();
		assert interfaceToken != null;
		final PsiKeyword interfaceKeyword = (PsiKeyword)interfaceToken.getOriginalElement();
		final PsiManager manager = anInterface.getManager();
		final PsiElementFactory factory = manager.getElementFactory();
		final PsiKeyword classKeyword = factory.createKeyword("class");
		interfaceKeyword.replace(classKeyword);

		final PsiModifierList classModifierList = anInterface.getModifierList();
		classModifierList.setModifierProperty(PsiModifier.ABSTRACT, true);
		final PsiElement parent = anInterface.getParent();
		if (parent instanceof PsiClass) {
			classModifierList.setModifierProperty(PsiModifier.STATIC, true);
		}
		final PsiMethod[] methods = anInterface.getMethods();
		for (final PsiMethod method : methods) {
			final PsiModifierList modifierList = method.getModifierList();
			modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
			modifierList.setModifierProperty(PsiModifier.ABSTRACT, true);
		}
		final PsiField[] fields = anInterface.getFields();
		for (final PsiField field : fields) {
			final PsiModifierList modifierList = field.getModifierList();
			modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
			modifierList.setModifierProperty(PsiModifier.STATIC, true);
			modifierList.setModifierProperty(PsiModifier.FINAL, true);
		}
	}

	@NotNull
	public String getFamilyName() {
		return "Convert Interface to Class";
	}

	public String getTextForElement(PsiElement element) {
		return "Convert interface to class";
	}

	protected void processIntention(@NotNull PsiElement element)
			throws IncorrectOperationException {
		final PsiClass anInterface = (PsiClass)element.getParent();
		moveSubClassImplementsToExtends(anInterface);
		changeInterfaceToClass(anInterface);
		moveExtendsToImplements(anInterface);
	}

	@NotNull
	protected PsiElementPredicate getElementPredicate() {
		return new ConvertInterfaceToClassPredicate();
	}

	private static void moveExtendsToImplements(PsiClass anInterface)
			throws IncorrectOperationException {
		final PsiReferenceList extendsList = anInterface.getExtendsList();
		final PsiReferenceList implementsList = anInterface.getImplementsList();
		assert extendsList != null;
		final PsiJavaCodeReferenceElement[] referenceElements =
				extendsList.getReferenceElements();
		for (final PsiJavaCodeReferenceElement referenceElement : referenceElements) {
			assert implementsList != null;
			implementsList.add(referenceElement);
			referenceElement.delete();
		}
	}

	private static void moveSubClassImplementsToExtends(PsiClass oldInterface)
			throws IncorrectOperationException {
		final PsiManager psiManager = oldInterface.getManager();
		final PsiSearchHelper searchHelper = psiManager.getSearchHelper();
		final PsiElementFactory elementFactory = psiManager.getElementFactory();
		final PsiJavaCodeReferenceElement oldInterfaceReference =
				elementFactory.createClassReferenceElement(oldInterface);
		final SearchScope searchScope = oldInterface.getUseScope();
		final PsiClass[] inheritors =
				searchHelper.findInheritors(oldInterface, searchScope, false);
		for (final PsiClass inheritor : inheritors) {
			final PsiReferenceList implementsList = inheritor.getImplementsList();
			final PsiReferenceList extendsList = inheritor.getExtendsList();
			if (implementsList != null) {
				moveReference(implementsList, extendsList, oldInterfaceReference);
			}
		}
	}

	private static void moveReference(@NotNull PsiReferenceList source,
	                                  @Nullable PsiReferenceList target,
	                                  @NotNull PsiJavaCodeReferenceElement reference)
			throws IncorrectOperationException {
		final PsiJavaCodeReferenceElement[] implementsReferences =
				source.getReferenceElements();
		final String fqName = reference.getQualifiedName();
		for (final PsiJavaCodeReferenceElement implementsReference : implementsReferences) {
			final String implementsReferenceFqName = implementsReference.getQualifiedName();
			if (fqName.equals(implementsReferenceFqName)) {
				if (target != null) {
					target.add(implementsReference);
				}
				implementsReference.delete();
			}
		}
	}
}