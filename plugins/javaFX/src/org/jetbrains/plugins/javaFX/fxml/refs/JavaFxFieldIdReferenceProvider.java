/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypeInfoImpl;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFieldFromUsageFix;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFieldFromUsageHelper;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import com.intellij.util.VisibilityUtil;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxClassBackedElementDescriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 1/17/13
 */
class JavaFxFieldIdReferenceProvider extends JavaFxControllerBasedReferenceProvider {
  @Override
  protected PsiReference[] getReferencesByElement(@NotNull final PsiClass aClass,
                                                  final XmlAttributeValue xmlAttributeValue,
                                                  ProcessingContext context) {
    final PsiField field = aClass.findFieldByName(xmlAttributeValue.getValue(), false);
    return new PsiReference[]{new JavaFxControllerFieldRef(xmlAttributeValue, field, aClass)};
  }

  public static class JavaFxControllerFieldRef extends PsiReferenceBase<XmlAttributeValue> {
    private final XmlAttributeValue myXmlAttributeValue;
    private final PsiField myField;
    private final PsiClass myAClass;

    public JavaFxControllerFieldRef(XmlAttributeValue xmlAttributeValue, PsiField field, PsiClass aClass) {
      super(xmlAttributeValue);
      myXmlAttributeValue = xmlAttributeValue;
      myField = field;
      myAClass = aClass;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      if (myField != null) {
        return myField;
      }
      else {
        if (myAClass != null) {
          final XmlFile xmlFile = (XmlFile)myXmlAttributeValue.getContainingFile();
          final XmlTag rootTag = xmlFile.getRootTag();
          if (rootTag != null) {
            if (!JavaFxPsiUtil.isOutOfHierarchy(myXmlAttributeValue) && !FxmlConstants.FX_ROOT.equals(rootTag.getName())) {
              return null;
            }
          }
        }
        return myXmlAttributeValue;
      }
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      final List<Object> fieldsToSuggest = new ArrayList<Object>();
      final PsiField[] fields = myAClass.getFields();
      for (PsiField psiField : fields) {
        if (!psiField.hasModifierProperty(PsiModifier.STATIC)) {
          if (JavaFxPsiUtil.isVisibleInFxml(psiField)) {
            fieldsToSuggest.add(psiField);
          }
        }
      }
      return ArrayUtil.toObjectArray(fieldsToSuggest);
    }
  }

  public static class JavaFxUnresolvedReferenceHandlerQuickfixProvider extends UnresolvedReferenceQuickFixProvider<JavaFxControllerFieldRef> {
    @Override
    public void registerFixes(final JavaFxControllerFieldRef ref, final QuickFixActionRegistrar registrar) {
      if (ref.myAClass != null && ref.myField == null) {
        final PsiClass fieldClass = CreateFieldFix.checkContext(ref.myXmlAttributeValue);
        if (fieldClass != null) {
          registrar.register(new CreateFieldFix(ref, fieldClass));
        }
      }
    }

    @NotNull
    @Override
    public Class<JavaFxControllerFieldRef> getReferenceClass() {
      return JavaFxControllerFieldRef.class;
    }

    private static class CreateFieldFix extends PsiElementBaseIntentionAction {
      private final PsiClass myFieldClass;
      private final PsiClass myClass;
      private final String myCanonicalText;

      public CreateFieldFix(JavaFxControllerFieldRef ref, PsiClass fieldClass) {
        myFieldClass = fieldClass;
        myClass = ref.myAClass;
        myCanonicalText = ref.getCanonicalText();
      }

      @Override
      public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
        
        if (!CodeInsightUtilBase.prepareFileForWrite(myClass.getContainingFile())) {
          return;
        }
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiField field = factory.createField(myCanonicalText, PsiType.INT);
        VisibilityUtil.setVisibility(field.getModifierList(), PsiModifier.PUBLIC);

        field = CreateFieldFromUsageHelper.insertField(myClass, field, element);

        final PsiClassType fieldType = factory.createType(myFieldClass);
        final ExpectedTypeInfo[] types = {new ExpectedTypeInfoImpl(fieldType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, 0, fieldType, TailType.NONE)};
        CreateFieldFromUsageFix.createFieldFromUsageTemplate(myClass, project, types, field, false, element);
      }

      protected static PsiClass checkContext(final XmlAttributeValue attributeValue) {
        if (attributeValue == null) return null;
        final PsiElement parent = attributeValue.getParent();
        if (parent instanceof XmlAttribute){
          final XmlTag tag = ((XmlAttribute)parent).getParent();
          if (tag != null) {
            final XmlElementDescriptor descriptor = tag.getDescriptor();
            if (descriptor instanceof JavaFxClassBackedElementDescriptor) {
              final PsiElement declaration = descriptor.getDeclaration();
              if (declaration instanceof PsiClass) {
                return (PsiClass)declaration;
              }
            }
          }
        }
        return null;
      }

      @Override
      public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        setText(QuickFixBundle.message("create.field.from.usage.text", myCanonicalText));
        return element.isValid();
      }

      @NotNull
      @Override
      public String getFamilyName() {
        return QuickFixBundle.message("create.field.from.usage.family");
      }
    }
  }
}
