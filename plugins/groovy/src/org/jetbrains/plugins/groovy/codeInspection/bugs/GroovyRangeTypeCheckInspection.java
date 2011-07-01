/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.arithmetic.GrRangeExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrRangeType;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.overrideImplement.GroovyOverrideImplementUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * @author Maxim.Medvedev
 */
public class GroovyRangeTypeCheckInspection extends BaseInspection {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyRangeTypeCheckInspection");

  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new MyVisitor();
  }

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return PROBABLE_BUGS;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return GroovyInspectionBundle.message("incorrect.range.argument");
  }

  @Override
  protected GroovyFix buildFix(PsiElement location) {
    final GrRangeExpression range = (GrRangeExpression)location;
    final PsiType type = range.getType();
    final List<GroovyFix> fixes = new ArrayList<GroovyFix>(3);
    if (type instanceof GrRangeType) {
      PsiType iterationType = ((GrRangeType)type).getIterationType();
      if (!(iterationType instanceof PsiClassType)) return null;
      final PsiClass psiClass = ((PsiClassType)iterationType).resolve();
      if (!(psiClass instanceof GrTypeDefinition)) return null;

      final GroovyResolveResult[] nexts = ResolveUtil.getMethodCandidates(iterationType, "next", range);
      final GroovyResolveResult[] previouses = ResolveUtil.getMethodCandidates(iterationType, "previous", range);
      final GroovyResolveResult[] compareTos = ResolveUtil.getMethodCandidates(iterationType, "compareTo", range, iterationType);


      if (countImplementations(psiClass, nexts)==0) {
        fixes.add(new AddMethodFix("next", (GrTypeDefinition)psiClass));
      }
      if (countImplementations(psiClass, previouses) == 0) {
        fixes.add(new AddMethodFix("previous", (GrTypeDefinition)psiClass));
      }

      if (!InheritanceUtil.isInheritor(iterationType, CommonClassNames.JAVA_LANG_COMPARABLE) ||
          countImplementations(psiClass, compareTos) == 0) {
        fixes.add(new AddClassToExtends((GrTypeDefinition)psiClass, CommonClassNames.JAVA_LANG_COMPARABLE));
      }

      return new GroovyFix() {
        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
          for (GroovyFix fix : fixes) {
            fix.applyFix(project, descriptor);
          }
        }

        @NotNull
        @Override
        public String getName() {
          return GroovyInspectionBundle.message("fix.class", psiClass.getName());
        }
      };
    }
    return null;
  }

  private static int countImplementations(PsiClass clazz, GroovyResolveResult[] methods) {
    if (clazz.isInterface()) return methods.length;
    int result = 0;
    for (GroovyResolveResult method : methods) {
      final PsiElement el = method.getElement();
      if (el instanceof PsiMethod && !((PsiMethod)el).hasModifierProperty(PsiModifier.ABSTRACT)) result++;
      else if (el instanceof PsiField) result++;
    }
    return result;
  }

  @Override
  protected String buildErrorString(Object... args) {
    switch (args.length) {
      case 1:
        return GroovyInspectionBundle.message("type.doesnt.implemnt.comparable", args);
      case 2:
        return GroovyInspectionBundle.message("type.doesnt.contain.method", args);
      default:
        throw new IncorrectOperationException("incorrect args:" + Arrays.toString(args));
    }
  }

  private static class MyVisitor extends BaseInspectionVisitor {
    @Override
    public void visitRangeExpression(GrRangeExpression range) {
      super.visitRangeExpression(range);
      final PsiType type = range.getType();
      if (!(type instanceof GrRangeType)) return;
      final PsiType iterationType = ((GrRangeType)type).getIterationType();
      if (iterationType == null) return;

      final GroovyResolveResult[] nexts = ResolveUtil.getMethodCandidates(iterationType, "next", range, PsiType.EMPTY_ARRAY);
      final GroovyResolveResult[] previouses = ResolveUtil.getMethodCandidates(iterationType, "previous", range, PsiType.EMPTY_ARRAY);
      if (nexts.length == 0) {
        registerError(range, iterationType.getPresentableText(), "next()");
      }
      if (previouses.length == 0) {
        registerError(range, iterationType.getPresentableText(), "previous()");
      }

      if (!InheritanceUtil.isInheritor(iterationType, CommonClassNames.JAVA_LANG_COMPARABLE)) {
        registerError(range, iterationType.getPresentableText());
      }
    }
  }

  private static class AddMethodFix extends GroovyFix {
    private final String myMethodName;
    private final GrTypeDefinition myClass;

    private AddMethodFix(String methodName, GrTypeDefinition aClass) {
      myMethodName = methodName;
      myClass = aClass;
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {

      if (myClass.isInterface()) {
        final GrMethod method = GroovyPsiElementFactory.getInstance(project)
          .createMethodFromText("def " + myClass.getName() + " " + myMethodName + "();");
        myClass.add(method);
      }
      else {
        String templName = JavaTemplateUtil.TEMPLATE_IMPLEMENTED_METHOD_BODY;
        final FileTemplate template = FileTemplateManager.getInstance().getCodeTemplate(templName);

        Properties properties = new Properties();

        String returnType = generateTypeText(myClass);
        properties.setProperty(FileTemplate.ATTRIBUTE_RETURN_TYPE, returnType);
        properties.setProperty(FileTemplate.ATTRIBUTE_DEFAULT_RETURN_VALUE,
                               PsiTypesUtil.getDefaultValueOfType(JavaPsiFacade.getElementFactory(project).createType(myClass)));
        properties.setProperty(FileTemplate.ATTRIBUTE_CALL_SUPER, "");
        properties.setProperty(FileTemplate.ATTRIBUTE_CLASS_NAME, myClass.getQualifiedName());
        properties.setProperty(FileTemplate.ATTRIBUTE_SIMPLE_CLASS_NAME, myClass.getName());
        properties.setProperty(FileTemplate.ATTRIBUTE_METHOD_NAME, myMethodName);

        try {
          String bodyText = StringUtil.replace(template.getText(properties), ";", "");
          final GrCodeBlock newBody = GroovyPsiElementFactory.getInstance(project).createMethodBodyFromText("\n" + bodyText + "\n");

          final GrMethod method = GroovyPsiElementFactory.getInstance(project)
            .createMethodFromText("", myMethodName, returnType, ArrayUtil.EMPTY_STRING_ARRAY, myClass);
          method.setBlock(newBody);
          myClass.add(method);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }


    @NotNull
    @Override
    public String getName() {
      return GroovyInspectionBundle.message("add.method", myMethodName, myClass.getName());
    }
  }

  private static String generateTypeText(GrTypeDefinition aClass) {
    StringBuilder returnType = new StringBuilder(aClass.getName());
    final PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
    if (typeParameters.length > 0) {
      returnType.append('<');
      for (PsiTypeParameter typeParameter : typeParameters) {
        returnType.append(typeParameter.getName()).append(", ");
      }
      returnType.replace(returnType.length() - 2, returnType.length(), ">");
    }
    return returnType.toString();
  }

  private static class AddClassToExtends extends GroovyFix {
    private GrTypeDefinition myPsiClass;
    private String myInterfaceName;

    public AddClassToExtends(GrTypeDefinition psiClass, String interfaceName) {
      myPsiClass = psiClass;
      myInterfaceName = interfaceName;
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {

      GrReferenceList list;
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

      final PsiClass comparable =
        JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_COMPARABLE, myPsiClass.getResolveScope());
      PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
      boolean addTypeParam = false;
      if (comparable != null) {
        final PsiTypeParameter[] typeParameters = comparable.getTypeParameters();
        if (typeParameters.length == 1) {
          final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
          final PsiTypeParameter[] classParams = myPsiClass.getTypeParameters();
          PsiSubstitutor innerSubstitutor = PsiSubstitutor.EMPTY;
          for (PsiTypeParameter classParam : classParams) {
            innerSubstitutor = innerSubstitutor.put(classParam, elementFactory.createType(classParam));
          }
          substitutor = substitutor.put(typeParameters[0], elementFactory.createType(myPsiClass, innerSubstitutor));
          addTypeParam = true;
        }
      }

      if (!InheritanceUtil.isInheritor(myPsiClass, CommonClassNames.JAVA_LANG_COMPARABLE)) {
        if (myPsiClass.isInterface()) {
          list = myPsiClass.getExtendsClause();
          if (list == null) {
            list = factory.createExtendsClause();

            PsiElement anchor = myPsiClass.getImplementsClause();
            if (anchor == null) {
              anchor = myPsiClass.getBody();
            }
            if (anchor == null) return;
            list = (GrReferenceList)myPsiClass.addBefore(list, anchor);
            myPsiClass.getNode().addLeaf(GroovyTokenTypes.mWS, " ", anchor.getNode());
            myPsiClass.getNode().addLeaf(GroovyTokenTypes.mWS, " ", list.getNode());
          }
        }
        else {
          list = myPsiClass.getImplementsClause();
          if (list == null) {
            list = factory.createImplementsClause();
            PsiElement anchor = myPsiClass.getBody();
            if (anchor == null) return;
            list = (GrReferenceList)myPsiClass.addBefore(list, anchor);
            myPsiClass.getNode().addLeaf(GroovyTokenTypes.mWS, " ", list.getNode());
            myPsiClass.getNode().addLeaf(GroovyTokenTypes.mWS, " ", anchor.getNode());
          }
        }


        final GrCodeReferenceElement _ref =
          factory.createReferenceElementFromText(myInterfaceName + (addTypeParam ? "<" + generateTypeText(myPsiClass) + ">" : ""));
        final GrCodeReferenceElement ref = (GrCodeReferenceElement)list.add(_ref);
        GrReferenceAdjuster.shortenReferences(ref);
      }
      if (comparable != null && !myPsiClass.isInterface()) {
        final PsiMethod baseMethod = comparable.getMethods()[0];
        final GrMethod prototype = GroovyOverrideImplementUtil.generateMethodPrototype(myPsiClass, baseMethod, substitutor);
        final PsiElement anchor = OverrideImplementUtil.getDefaultAnchorToOverrideOrImplement(myPsiClass, baseMethod, substitutor);
        GenerateMembersUtil.insert(myPsiClass, prototype, anchor, true);
      }
    }

    @NotNull
    @Override
    public String getName() {
      return GroovyInspectionBundle.message("implement.class", myInterfaceName);
    }
  }
}
