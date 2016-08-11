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
package org.jetbrains.plugins.groovy.refactoring.memberPullUp;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.memberPullUp.PullUpData;
import com.intellij.refactoring.memberPullUp.PullUpHelper;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl.GrDocCommentUtil;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyChangeContextUtil;
import org.jetbrains.plugins.groovy.refactoring.classMembers.GrClassMemberReferenceVisitor;

import java.util.*;

/**
 * Created by Max Medvedev on 10/4/13
 */
public class GrPullUpHelper implements PullUpHelper<MemberInfo> {
  private static final Logger LOG = Logger.getInstance(GrPullUpHelper.class);

  private static final Key<Boolean> SUPER_REF = Key.create("SUPER_REF");
  private static final Key<Boolean> THIS_REF = Key.create("THIS_REF");
  private static final Key<Boolean> PRESERVE_QUALIFIER = Key.create("PRESERVE_QUALIFIER");

  private final PsiClass myTargetSuperClass;
  private final Set<PsiMember> myMembersToMove;
  private final PsiClass mySourceClass;
  private final Project myProject;
  private final DocCommentPolicy myDocCommentPolicy;
  private final Set<PsiMember> myMembersAfterMove;

  final ExplicitSuperDeleter myExplicitSuperDeleter;
  final QualifiedThisSuperAdjuster myThisSuperAdjuster;
  private final QualifiedThisSuperSearcher myQualifiedSearcher;

  public GrPullUpHelper(PullUpData data) {
    myTargetSuperClass = data.getTargetClass();
    myMembersToMove = data.getMembersToMove();
    mySourceClass = data.getSourceClass();
    myProject = data.getProject();
    myDocCommentPolicy = data.getDocCommentPolicy();
    myMembersAfterMove = data.getMovedMembers();
    myExplicitSuperDeleter = new ExplicitSuperDeleter();
    myThisSuperAdjuster = new QualifiedThisSuperAdjuster();

    myQualifiedSearcher = new QualifiedThisSuperSearcher();
  }

  @Override
  public void encodeContextInfo(MemberInfo info) {
    GroovyChangeContextUtil.encodeContextInfo(info.getMember());

    ((GroovyPsiElement)info.getMember()).accept(myQualifiedSearcher);
  }

  @Override
  public void move(MemberInfo info, PsiSubstitutor substitutor) {
    if (info.getMember() instanceof PsiMethod) {
      doMoveMethod(substitutor, info);
    }
    else if (info.getMember() instanceof PsiField) {
      doMoveField(substitutor, info);
    }
    else if (info.getMember() instanceof PsiClass) {
      doMoveClass(substitutor, info);
    }
  }

  @Override
  public void postProcessMember(PsiMember member) {
    ((GrMember)member).accept(myExplicitSuperDeleter);
    ((GrMember)member).accept(myThisSuperAdjuster);

    GroovyChangeContextUtil.decodeContextInfo(member, null, null);

    ((GroovyPsiElement)member).accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
        if (processRef(referenceExpression)) return;
        super.visitReferenceExpression(referenceExpression);
      }

      @Override
      public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
        if (processRef(refElement)) return;
        super.visitCodeReferenceElement(refElement);
      }

      private boolean processRef(@NotNull GrReferenceElement<? extends GroovyPsiElement> refElement) {
        final PsiElement qualifier = refElement.getQualifier();
        if (qualifier != null) {
          final Boolean preserveQualifier = qualifier.getCopyableUserData(PRESERVE_QUALIFIER);
          if (preserveQualifier != null && !preserveQualifier) {
            refElement.setQualifier(null);
            return true;
          }
        }
        return false;
      }
    });

  }

  @Override
  public void setCorrectVisibility(MemberInfo info) {
    PsiModifierListOwner modifierListOwner = info.getMember();
    if (myTargetSuperClass.isInterface()) {
      PsiUtil.setModifierProperty(modifierListOwner, PsiModifier.PUBLIC, true);
    }
    else if (modifierListOwner.hasModifierProperty(PsiModifier.PRIVATE)) {
      if (info.isToAbstract() || willBeUsedInSubclass(modifierListOwner, myMembersToMove, myTargetSuperClass, mySourceClass)) {
        PsiUtil.setModifierProperty(modifierListOwner, PsiModifier.PROTECTED, true);
      }

      if (modifierListOwner instanceof GrTypeDefinition) {
        ((GrTypeDefinition)modifierListOwner).accept(new GroovyRecursiveElementVisitor() {
          @Override
          public void visitMethod(GrMethod method) {
            check(method);
          }

          @Override
          public void visitField(GrField field) {
            check(field);
          }

          @Override
          public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
            check(typeDefinition);
            super.visitTypeDefinition(typeDefinition);
          }

          private void check(PsiMember member) {
            if (member.hasModifierProperty(PsiModifier.PRIVATE)) {
              if (willBeUsedInSubclass(member, myMembersToMove, myTargetSuperClass, mySourceClass)) {
                PsiUtil.setModifierProperty(member, PsiModifier.PROTECTED, true);
              }
            }
          }
        });
      }
    }

  }

  @Override
  public void moveFieldInitializations(LinkedHashSet<PsiField> movedFields) {
    //todo
  }

  @Override
  public void updateUsage(PsiElement element) {
    if (element instanceof GrReferenceExpression) {
      GrExpression qualifierExpression = ((GrReferenceExpression)element).getQualifierExpression();
      if (qualifierExpression instanceof GrReferenceExpression && ((GrReferenceExpression)qualifierExpression).resolve() == mySourceClass) {
        ((GrReferenceExpression)qualifierExpression).bindToElement(myTargetSuperClass);
      }
    }
  }

  private static boolean willBeUsedInSubclass(PsiElement member, Set<PsiMember> movedMembers, PsiClass superclass, PsiClass subclass) {
    for (PsiReference ref : ReferencesSearch.search(member, new LocalSearchScope(subclass), false)) {
      PsiElement element = ref.getElement();
      if (!RefactoringHierarchyUtil.willBeInTargetClass(element, movedMembers, superclass, false)) {
        return true;
      }
    }

    return false;
  }

  private void doMoveMethod(PsiSubstitutor substitutor, MemberInfo info) {
    GroovyPsiElementFactory elementFactory = GroovyPsiElementFactory.getInstance(myProject);
    GrMethod method = (GrMethod)info.getMember();
    PsiMethod sibling = method;
    PsiMethod anchor = null;
    while (sibling != null) {
      sibling = PsiTreeUtil.getNextSiblingOfType(sibling, PsiMethod.class);
      if (sibling != null) {
        anchor = MethodSignatureUtil.findMethodInSuperClassBySignatureInDerived(method.getContainingClass(), myTargetSuperClass,
                                                                                sibling.getSignature(PsiSubstitutor.EMPTY), false);
        if (anchor != null) {
          break;
        }
      }
    }

    GrMethod methodCopy = (GrMethod)method.copy();
    if (method.findSuperMethods(myTargetSuperClass).length == 0) {
      deleteOverrideAnnotationIfFound(methodCopy);
    }

    final boolean isOriginalMethodAbstract =
      method.hasModifierProperty(PsiModifier.ABSTRACT) || method.hasModifierProperty(PsiModifier.DEFAULT);
    if (myTargetSuperClass.isInterface() || info.isToAbstract()) {
      GroovyChangeContextUtil.clearContextInfo(method);
      RefactoringUtil.makeMethodAbstract(myTargetSuperClass, methodCopy);
      if (myTargetSuperClass.isInterface()) {
        PsiUtil.setModifierProperty(methodCopy, PsiModifier.ABSTRACT, false);
      }
      replaceMovedMemberTypeParameters(methodCopy, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);

      final GrMethod movedElement =
        anchor != null ? (GrMethod)myTargetSuperClass.addBefore(methodCopy, anchor) : (GrMethod)myTargetSuperClass.add(methodCopy);
      CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(method.getProject());
      if (styleSettings.INSERT_OVERRIDE_ANNOTATION) {
        if (PsiUtil.isLanguageLevel5OrHigher(mySourceClass) && !myTargetSuperClass.isInterface() ||
            PsiUtil.isLanguageLevel6OrHigher(mySourceClass)) {
          new AddAnnotationFix(CommonClassNames.JAVA_LANG_OVERRIDE, method)
            .invoke(method.getProject(), null, mySourceClass.getContainingFile());
        }
      }

      GrDocComment oldDoc = method.getDocComment();
      if (oldDoc != null) {
        GrDocCommentUtil.setDocComment(movedElement, oldDoc);
      }

      myDocCommentPolicy.processCopiedJavaDoc(methodCopy.getDocComment(), oldDoc, isOriginalMethodAbstract);

      myMembersAfterMove.add(movedElement);
      if (isOriginalMethodAbstract) {
        deleteMemberWithDocComment(method);
      }
    }
    else {
      if (isOriginalMethodAbstract) {
        PsiUtil.setModifierProperty(myTargetSuperClass, PsiModifier.ABSTRACT, true);
      }

      fixReferencesToStatic(methodCopy);
      replaceMovedMemberTypeParameters(methodCopy, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);
      final PsiMethod superClassMethod = myTargetSuperClass.findMethodBySignature(methodCopy, false);

      Language language = myTargetSuperClass.getLanguage();
      final PsiMethod movedElement;
      if (superClassMethod != null && superClassMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
        movedElement = (PsiMethod)superClassMethod.replace(convertMethodToLanguage(methodCopy, language));
      }
      else {
        movedElement = anchor != null
                       ? (PsiMethod)myTargetSuperClass.addBefore(convertMethodToLanguage(methodCopy, language), anchor)
                       : (PsiMethod)myTargetSuperClass.add(convertMethodToLanguage(methodCopy, language));
        myMembersAfterMove.add(movedElement);
      }

      if (movedElement instanceof GrMethod) {
        GrDocCommentUtil.setDocComment((GrDocCommentOwner)movedElement, method.getDocComment());
      }

      deleteMemberWithDocComment(method);
    }
  }

  private static void deleteMemberWithDocComment(GrDocCommentOwner docCommentOwner) {
    GrDocComment oldDoc = docCommentOwner.getDocComment();
    if (oldDoc != null) {
      oldDoc.delete();
    }
    docCommentOwner.delete();
  }

  private static void deleteOverrideAnnotationIfFound(PsiMethod oMethod) {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(oMethod, CommonClassNames.JAVA_LANG_OVERRIDE);
    if (annotation != null) {
      PsiElement prev = annotation.getPrevSibling();
      PsiElement next = annotation.getNextSibling();
      if ((prev == null || org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isLineFeed(prev)) &&
          org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isLineFeed(next)) {
        next.delete();
      }
      annotation.delete();
    }
  }

  public static void replaceMovedMemberTypeParameters(final PsiElement member,
                                                      final Iterable<PsiTypeParameter> parametersIterable,
                                                      final PsiSubstitutor substitutor,
                                                      final GroovyPsiElementFactory factory) {
    final Map<PsiElement, PsiElement> replacement = new LinkedHashMap<>();
    for (PsiTypeParameter parameter : parametersIterable) {
      PsiType substitutedType = substitutor.substitute(parameter);

      PsiType type = substitutedType != null ? substitutedType : TypeConversionUtil.erasure(factory.createType(parameter));

      PsiElement scopeElement = member instanceof GrField ? member.getParent() : member;
      for (PsiReference reference : ReferencesSearch.search(parameter, new LocalSearchScope(scopeElement))) {
        final PsiElement element = reference.getElement();
        final PsiElement parent = element.getParent();
        if (parent instanceof PsiTypeElement) {
          replacement.put(parent, factory.createTypeElement(type));
        }
        else if (element instanceof GrCodeReferenceElement && type instanceof PsiClassType) {
          replacement.put(element, factory.createReferenceElementByType((PsiClassType)type));
        }
      }
    }

    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(member.getProject());
    for (PsiElement element : replacement.keySet()) {
      if (element.isValid()) {
        final PsiElement replaced = element.replace(replacement.get(element));
        codeStyleManager.shortenClassReferences(replaced);
      }
    }
  }

  private void fixReferencesToStatic(GroovyPsiElement classMember) throws IncorrectOperationException {
    final StaticReferencesCollector collector = new StaticReferencesCollector(myMembersToMove);
    classMember.accept(collector);
    ArrayList<GrReferenceElement> refs = collector.getReferences();
    ArrayList<PsiElement> members = collector.getReferees();
    ArrayList<PsiClass> classes = collector.getRefereeClasses();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myProject);

    for (int i = 0; i < refs.size(); i++) {
      GrReferenceElement ref = refs.get(i);
      PsiElement namedElement = members.get(i);
      PsiClass aClass = classes.get(i);

      if (namedElement instanceof PsiNamedElement) {
        GrReferenceExpression newRef = (GrReferenceExpression)factory.createExpressionFromText("a." + ((PsiNamedElement)namedElement).getName(), null);
        GrExpression qualifier = newRef.getQualifierExpression();
        assert qualifier != null;
        qualifier = (GrExpression)qualifier.replace(factory.createReferenceExpressionFromText(aClass.getQualifiedName()));
        qualifier.putCopyableUserData(PRESERVE_QUALIFIER, ref.isQualified());
        PsiElement replaced = ref.replace(newRef);
        JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(replaced);
      }
    }
  }


  private class StaticReferencesCollector extends GrClassMemberReferenceVisitor {
    private final ArrayList<GrReferenceElement> myReferences = new ArrayList<>();
    private final ArrayList<PsiElement> myReferees = new ArrayList<>();
    private final ArrayList<PsiClass> myRefereeClasses = new ArrayList<>();
    private final Set<PsiMember> myMovedMembers;

    private StaticReferencesCollector(Set<PsiMember> movedMembers) {
      super(mySourceClass);
      myMovedMembers = movedMembers;
    }

    public ArrayList<PsiElement> getReferees() {
      return myReferees;
    }

    public ArrayList<PsiClass> getRefereeClasses() {
      return myRefereeClasses;
    }

    public ArrayList<GrReferenceElement> getReferences() {
      return myReferences;
    }

    @Override
    protected void visitClassMemberReferenceElement(GrMember classMember, GrReferenceElement classMemberReference) {
      if (classMember.hasModifierProperty(PsiModifier.STATIC) /*&& classMemberReference.isQualified()*/) {
        if (!myMovedMembers.contains(classMember) &&
            RefactoringHierarchyUtil.isMemberBetween(myTargetSuperClass, mySourceClass, classMember)) {
          myReferences.add(classMemberReference);
          myReferees.add(classMember);
          myRefereeClasses.add(classMember.getContainingClass());
        }
        else if (myMovedMembers.contains(classMember) || myMembersAfterMove.contains(classMember)) {
          myReferences.add(classMemberReference);
          myReferees.add(classMember);
          myRefereeClasses.add(myTargetSuperClass);
        }
      }
    }
  }

  private class ExplicitSuperDeleter extends GroovyRecursiveElementVisitor {
    private final GrExpression myThisExpression = GroovyPsiElementFactory.getInstance(myProject).createExpressionFromText("this", null);

    @Override
    public void visitReferenceExpression(GrReferenceExpression expression) {
      if(org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isSuperReference(expression.getQualifierExpression())) {
        PsiElement resolved = expression.resolve();
        if (resolved == null || resolved instanceof PsiMethod && shouldFixSuper((PsiMethod) resolved)) {
          expression.setQualifier(null);
        }
      }
      else if (org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isSuperReference(expression)) {
        expression.replaceWithExpression(myThisExpression, true);
      }
    }

    @Override
    public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
      //do nothing
    }

    private boolean shouldFixSuper(PsiMethod method) {
      for (PsiMember element : myMembersAfterMove) {
        if (element instanceof PsiMethod) {
          PsiMethod member = (PsiMethod)element;
          // if there is such member among moved members, super qualifier
          // should not be removed
          final PsiManager manager = method.getManager();
          if (manager.areElementsEquivalent(member.getContainingClass(), method.getContainingClass()) &&
              MethodSignatureUtil.areSignaturesEqual(member, method)) {
            return false;
          }
        }
      }

      final PsiMethod methodFromSuper = myTargetSuperClass.findMethodBySignature(method, false);
      return methodFromSuper == null;
    }
  }

  private class QualifiedThisSuperAdjuster extends GroovyRecursiveElementVisitor {
    @Override
    public void visitReferenceExpression(GrReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (expression.getCopyableUserData(SUPER_REF) != null) {
        expression.putCopyableUserData(SUPER_REF, null);
        final GrExpression qualifier = expression.getQualifier();
        if (qualifier instanceof GrReferenceExpression && ((GrReferenceExpression)qualifier).isReferenceTo(mySourceClass)) {
          try {
            GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myProject);
            GrExpression newExpr = factory.createExpressionFromText(myTargetSuperClass.getName() + ".this", null);
            expression.replace(newExpr);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
      else if (expression.getCopyableUserData(THIS_REF) != null) {
        expression.putCopyableUserData(THIS_REF, null);
        final GrExpression qualifier = expression.getQualifier();
        if (qualifier instanceof GrReferenceExpression && ((GrReferenceExpression)qualifier).isReferenceTo(mySourceClass)) {
          try {
            ((GrReferenceExpression)qualifier).bindToElement(myTargetSuperClass);
            GroovyChangeContextUtil.clearContextInfo(qualifier);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    }
  }

  private void doMoveField(PsiSubstitutor substitutor, MemberInfo info) {
    GroovyPsiElementFactory elementFactory = GroovyPsiElementFactory.getInstance(myProject);
    GrField field = (GrField)info.getMember();
    field.normalizeDeclaration();
    replaceMovedMemberTypeParameters(field, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);
    fixReferencesToStatic(field);
    if (myTargetSuperClass.isInterface()) {
      PsiUtil.setModifierProperty(field, PsiModifier.PUBLIC, true);
    }
    final PsiMember movedElement = (PsiMember)myTargetSuperClass.add(convertFieldToLanguage(field, myTargetSuperClass.getLanguage()));
    myMembersAfterMove.add(movedElement);
    deleteMemberWithDocComment(field);
  }

  private void doMoveClass(PsiSubstitutor substitutor, MemberInfo info) {
    if (Boolean.FALSE.equals(info.getOverrides())) {
      PsiClass aClass = (PsiClass)info.getMember();
      if (myTargetSuperClass instanceof GrTypeDefinition) {
        addClassToSupers(info, aClass, substitutor, (GrTypeDefinition)myTargetSuperClass);
      }

    }
    else {
      GrTypeDefinition aClass = (GrTypeDefinition)info.getMember();
      GroovyPsiElementFactory elementFactory = GroovyPsiElementFactory.getInstance(myProject);
      replaceMovedMemberTypeParameters(aClass, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);
      fixReferencesToStatic(aClass);
      PsiMember movedElement = (PsiMember)myTargetSuperClass.addAfter(convertClassToLanguage(aClass, myTargetSuperClass.getLanguage()), null);
      myMembersAfterMove.add(movedElement);
      deleteMemberWithDocComment(aClass);
    }
  }

  private static PsiMethod convertMethodToLanguage(PsiMethod method, Language language) {
    if (method.getLanguage().equals(language)) {
      return method;
    }
    return JVMElementFactories.getFactory(language, method.getProject()).createMethodFromText(method.getText(), null);
  }

  private static PsiField convertFieldToLanguage(PsiField field, Language language) {
    if (field.getLanguage().equals(language)) {
      return field;
    }
    return JVMElementFactories.getFactory(language, field.getProject()).createField(field.getName(), field.getType());
  }

  private static PsiClass convertClassToLanguage(PsiClass clazz, Language language) {
    //if (clazz.getLanguage().equals(language)) {
    //  return clazz;
    //}
    //PsiClass newClass = JVMElementFactories.getFactory(language, clazz.getProject()).createClass(clazz.getName());
    return clazz;
  }


  private void addClassToSupers(MemberInfo info, PsiClass aClass, PsiSubstitutor substitutor, GrTypeDefinition targetSuperClass) {
    final PsiReferenceList sourceReferenceList = info.getSourceReferenceList();
    LOG.assertTrue(sourceReferenceList != null);
    PsiQualifiedReferenceElement ref = mySourceClass.equals(sourceReferenceList.getParent()) ?
                                 removeFromReferenceList(sourceReferenceList, aClass) :
                                 findReferenceToClass(sourceReferenceList, aClass);
    if (ref != null && !targetSuperClass.isInheritor(aClass, false)) {
      GroovyPsiElementFactory elementFactory = GroovyPsiElementFactory.getInstance(myProject);

      replaceMovedMemberTypeParameters(ref, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);
      GrReferenceList referenceList;
      if (targetSuperClass.isInterface()) {
        referenceList = targetSuperClass.getExtendsClause();
        if (referenceList == null) {
          GrExtendsClause newClause = GroovyPsiElementFactory.getInstance(myProject).createExtendsClause();
          PsiElement anchor = targetSuperClass.getTypeParameterList() != null ? targetSuperClass.getTypeParameterList():
                              targetSuperClass.getNameIdentifierGroovy();
          referenceList = (GrReferenceList)targetSuperClass.addAfter(newClause, anchor);
          addSpacesAround(referenceList);
        }
      }
      else {
        referenceList = targetSuperClass.getImplementsClause();

        if (referenceList == null) {
          GrImplementsClause newClause = GroovyPsiElementFactory.getInstance(myProject).createImplementsClause();
          PsiElement anchor = targetSuperClass.getExtendsClause() != null ? targetSuperClass.getExtendsClause() :
                              targetSuperClass.getTypeParameterList() != null ? targetSuperClass.getTypeParameterList() :
                              targetSuperClass.getNameIdentifierGroovy();
          referenceList = (GrReferenceList)targetSuperClass.addAfter(newClause, anchor);
          addSpacesAround(referenceList);
        }

      }

      assert referenceList != null;
      referenceList.add(ref);
    }
  }

  private static void addSpacesAround(@NotNull GrReferenceList list) {
    PsiElement prev = list.getPrevSibling();
    if (!PsiImplUtil.isWhiteSpaceOrNls(prev)) {
      list.getParent().getNode().addLeaf(TokenType.WHITE_SPACE, " ", list.getNode());
    }

    PsiElement next = list.getNextSibling();
    if (!PsiImplUtil.isWhiteSpaceOrNls(next)) {
      list.getParent().getNode().addLeaf(TokenType.WHITE_SPACE, " ", list.getNode().getTreeNext());
    }
  }

  public static PsiQualifiedReferenceElement findReferenceToClass(PsiReferenceList refList, PsiClass aClass) {
    PsiQualifiedReferenceElement[] refs = refList instanceof GrReferenceList ? ((GrReferenceList)refList).getReferenceElementsGroovy()
                                                                             : refList.getReferenceElements();

    for (PsiQualifiedReferenceElement ref : refs) {
      if (ref.isReferenceTo(aClass)) {
        return ref;
      }
    }

    return null;
  }


  /**
   * removes a reference to the specified class from the reference list given
   *
   * @return if removed  - a reference to the class or null if there were no references to this class in the reference list
   */
  public static PsiQualifiedReferenceElement removeFromReferenceList(PsiReferenceList refList, PsiClass aClass) throws IncorrectOperationException {
    List<? extends PsiQualifiedReferenceElement> refs = Arrays.asList(
      refList instanceof GrReferenceList ? ((GrReferenceList)refList).getReferenceElementsGroovy() : refList.getReferenceElements());

    for (PsiQualifiedReferenceElement ref : refs) {
      if (ref.isReferenceTo(aClass)) {
        PsiQualifiedReferenceElement refCopy = (PsiQualifiedReferenceElement)ref.copy();
        ref.delete();
        return refCopy;
      }
    }
    return null;
  }

  private class QualifiedThisSuperSearcher extends GroovyRecursiveElementVisitor {
    @Override
    public void visitReferenceExpression(GrReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isSuperReference(expression)) {
        final GrExpression qualifier = expression.getQualifier();
        if (qualifier instanceof GrReferenceExpression && ((GrReferenceExpression)qualifier).isReferenceTo(mySourceClass)) {
          try {
            expression.putCopyableUserData(SUPER_REF, Boolean.TRUE);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
      else if (org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isThisReference(expression)) {
        final GrExpression qualifier = expression.getQualifier();
        if (qualifier instanceof GrReferenceExpression && ((GrReferenceExpression)qualifier).isReferenceTo(mySourceClass)) {
          try {
            expression.putCopyableUserData(THIS_REF, Boolean.TRUE);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }

    }
  }
}
