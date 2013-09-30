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
package org.jetbrains.plugins.groovy.refactoring.memberPullUp;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.JavaRefactoringListenerManager;
import com.intellij.refactoring.listeners.impl.JavaRefactoringListenerManagerImpl;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
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
import org.jetbrains.plugins.groovy.refactoring.classMembers.GrMemberInfo;

import java.util.*;

/**
 * @author Max Medvedev
 */
public class GrPullUpHelper extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(GrPullUpHelper.class);
  private static final Key<Boolean> SUPER_REF = Key.create("SUPER_REF");
  private static final Key<Boolean> THIS_REF = Key.create("THIS_REF");
  private static final Key<Boolean> PRESERVE_QUALIFIER = Key.create("PRESERVE_QUALIFIER");

  private PsiClass mySourceClass;
  private GrTypeDefinition myTargetSuperClass;
  private GrMemberInfo[] myMembersToMove;
  private DocCommentPolicy myDocCommentPolicy;
  private Set<PsiMember> myMembersAfterMove;


  public GrPullUpHelper(PsiClass aClass, PsiClass superClass, GrMemberInfo[] infos, DocCommentPolicy policy) {
    super(aClass.getProject());

    mySourceClass = aClass;
    myTargetSuperClass = (GrTypeDefinition)superClass;
    myMembersToMove = infos;
    myDocCommentPolicy = policy;
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new UsageViewDescriptor() {
      public String getProcessedElementsHeader() {
        return "Pull up members from";
      }

      @NotNull
      public PsiElement[] getElements() {
        return new PsiClass[]{mySourceClass};
      }

      public String getCodeReferencesText(int usagesCount, int filesCount) {
        return "Class to pull up members to \"" + RefactoringUIUtil.getDescription(myTargetSuperClass, true) + "\"";
      }

      public String getCommentReferencesText(int usagesCount, int filesCount) {
        return null;
      }
    };
  }

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    final List<UsageInfo> result = new ArrayList<UsageInfo>();
    for (GrMemberInfo info : myMembersToMove) {
      final PsiMember member = info.getMember();
      if (member.hasModifierProperty(PsiModifier.STATIC)) {
        for (PsiReference reference : ReferencesSearch.search(member)) {
          result.add(new UsageInfo(reference));
        }
      }
    }

    return DefaultGroovyMethods.asType(result, UsageInfo[].class);
  }

  @Override
  protected void performRefactoring(UsageInfo[] usages) {
    moveMembersToBase();
    //moveFieldInitializations(); todo
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element instanceof GrReferenceExpression) {
        GrExpression qualifier = ((GrReferenceExpression)element).getQualifier();
        if (qualifier instanceof GrReferenceExpression && ((GrReferenceExpression)qualifier).resolve().equals(mySourceClass)) {
          ((GrReferenceExpression)qualifier).bindToElement(myTargetSuperClass);
        }
      }
    }

    /*
    todo
    ApplicationManager.application.invokeLater(new Runnable() {
      @Override
      public void run() {
        processMethodsDuplicates();
      }
    }, ModalityState.NON_MODAL, myProject.getDisposed());*/

  }

  @Override
  protected String getCommandName() {
    return RefactoringBundle.message("pullUp.command", DescriptiveNameUtil.getDescriptiveName(mySourceClass));
  }

  public void moveMembersToBase() throws IncorrectOperationException {
    final HashSet<PsiMember> movedMembers = ContainerUtil.newHashSet();
    myMembersAfterMove = ContainerUtil.newHashSet();

    // build aux sets
    for (GrMemberInfo info : myMembersToMove) {
      movedMembers.add(info.getMember());
    }


    // correct private member visibility
    for (GrMemberInfo info : myMembersToMove) {
      if (info.getMember() instanceof PsiClass && info.getOverrides() != null) continue;
      setCorrectVisibility(movedMembers, info);
      GroovyChangeContextUtil.encodeContextInfo(info.getMember());
      info.getMember().accept(new QualifiedThisSuperSearcher());
      fixReferencesToStatic(info.getMember(), movedMembers);
    }




    final PsiSubstitutor substitutor = upDownSuperClassSubstitutor();

    // do actual move
    for (GrMemberInfo info : myMembersToMove) {
      if (info.getMember() instanceof PsiMethod) {
        doMoveMethod(substitutor, info);
      }
      else if (info.getMember() instanceof GrField) {
        doMoveField(substitutor, info);
      }
      else if (info.getMember() instanceof PsiClass) {
        doMoveClass(substitutor, info);
      }
    }


    ExplicitSuperDeleter explicitSuperDeleter = new ExplicitSuperDeleter();
    for (PsiMember member : myMembersAfterMove) {
      ((GrMember)member).accept(explicitSuperDeleter);
    }
    explicitSuperDeleter.fixSupers();

    final QualifiedThisSuperAdjuster qualifiedThisSuperAdjuster = new QualifiedThisSuperAdjuster();
    for (PsiMember member : myMembersAfterMove) {
      ((GrMember)member).accept(qualifiedThisSuperAdjuster);
    }

    for (PsiMember member : myMembersAfterMove) {
      GroovyChangeContextUtil.decodeContextInfo(member, null, null);
    }


    final JavaRefactoringListenerManagerImpl listenerManager = (JavaRefactoringListenerManagerImpl)JavaRefactoringListenerManager.getInstance(myProject);
    for (final PsiMember movedMember : myMembersAfterMove) {
      ((GroovyPsiElement)movedMember).accept(new GroovyRecursiveElementVisitor() {
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
      listenerManager.fireMemberMoved(mySourceClass, movedMember);
    }
  }

  private PsiSubstitutor upDownSuperClassSubstitutor() {
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(mySourceClass)) {
      substitutor = substitutor.put(parameter, null);
    }

    final Map<PsiTypeParameter, PsiType> substitutionMap =
      TypeConversionUtil.getSuperClassSubstitutor(myTargetSuperClass, mySourceClass, PsiSubstitutor.EMPTY).getSubstitutionMap();
    for (PsiTypeParameter parameter : substitutionMap.keySet()) {
      final PsiType type = substitutionMap.get(parameter);
      final PsiClass resolvedClass = PsiUtil.resolveClassInType(type);
      if (resolvedClass instanceof PsiTypeParameter) {
        substitutor = substitutor.put((PsiTypeParameter)resolvedClass, JavaPsiFacade.getElementFactory(myProject).createType(parameter));
      }
    }

    return substitutor;
  }

  public void setCorrectVisibility(final HashSet<PsiMember> movedMembers, GrMemberInfo info) {
    PsiModifierListOwner modifierListOwner = info.getMember();
    if (myTargetSuperClass.isInterface()) {
      PsiUtil.setModifierProperty(modifierListOwner, PsiModifier.PUBLIC, true);
    }
    else if (modifierListOwner.hasModifierProperty(PsiModifier.PRIVATE)) {
      if (info.isToAbstract() || willBeUsedInSubclass(modifierListOwner, movedMembers, myTargetSuperClass, mySourceClass)) {
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
              if (willBeUsedInSubclass(member, movedMembers, myTargetSuperClass, mySourceClass)) {
                PsiUtil.setModifierProperty(member, PsiModifier.PROTECTED, true);
              }
            }
          }
        });
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

  private void doMoveMethod(PsiSubstitutor substitutor, GrMemberInfo info) {
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

      //fixReferencesToStatic(methodCopy, movedMembers);
      replaceMovedMemberTypeParameters(methodCopy, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);
      final PsiMethod superClassMethod = myTargetSuperClass.findMethodBySignature(methodCopy, false);
      final GrMethod movedElement;
      if (superClassMethod != null && superClassMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
        movedElement = (GrMethod)superClassMethod.replace(methodCopy);
      }
      else {
        movedElement =
          anchor != null ? (GrMethod)myTargetSuperClass.addBefore(methodCopy, anchor) : (GrMethod)myTargetSuperClass.add(methodCopy);
        myMembersAfterMove.add(movedElement);
      }

      GrDocCommentUtil.setDocComment(movedElement, method.getDocComment());

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
    final Map<PsiElement, PsiElement> replacement = new LinkedHashMap<PsiElement, PsiElement>();
    for (PsiTypeParameter parameter : parametersIterable) {
      PsiType substitutedType = substitutor.substitute(parameter);
      if (substitutedType == null) {
        substitutedType = TypeConversionUtil.erasure(factory.createType(parameter));
      }

      PsiElement scopeElement = member instanceof GrField ? member.getParent() : member;
      for (PsiReference reference : ReferencesSearch.search(parameter, new LocalSearchScope(scopeElement))) {
        final PsiElement element = reference.getElement();
        final PsiElement parent = element.getParent();
        if (parent instanceof PsiTypeElement) {
          replacement.put(parent, factory.createTypeElement(substitutedType));
        }
        else if (element instanceof GrCodeReferenceElement && substitutedType instanceof PsiClassType) {
          replacement.put(element, factory.createReferenceElementByType((PsiClassType)substitutedType));
        }
      }
    }

    for (PsiElement element : replacement.keySet()) {
      if (element.isValid()) {
        element.replace(replacement.get(element));
      }
    }
  }

  private void fixReferencesToStatic(GroovyPsiElement classMember, Set<PsiMember> movedMembers) throws IncorrectOperationException {
    final StaticReferencesCollector collector = new StaticReferencesCollector(movedMembers);
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
    private final ArrayList<GrReferenceElement> myReferences = new ArrayList<GrReferenceElement>();
    private final ArrayList<PsiElement> myReferees = new ArrayList<PsiElement>();
    private final ArrayList<PsiClass> myRefereeClasses = new ArrayList<PsiClass>();
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
    private final ArrayList<GrExpression> mySupersToDelete = ContainerUtil.newArrayList();
    private final ArrayList<GrReferenceExpression> mySupersToChangeToThis = ContainerUtil.newArrayList();

    @Override
    public void visitReferenceExpression(GrReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if(org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isSuperReference(expression.getQualifierExpression())) {
        PsiElement resolved = expression.resolve();
        if (resolved == null || resolved instanceof PsiMethod && shouldFixSuper((PsiMethod) resolved)) {
          mySupersToDelete.add(expression.getQualifierExpression());
        }
      }
      else if (org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isSuperReference(expression)) {
        mySupersToChangeToThis.add(expression);
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

    public void fixSupers() throws IncorrectOperationException {
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myProject);
      GrReferenceExpression thisExpression = (GrReferenceExpression) factory.createExpressionFromText("this", null);
      for (GrExpression expression : mySupersToDelete) {
        expression.delete();
      }

      for (GrReferenceExpression superExpression : mySupersToChangeToThis) {
        superExpression.replace(thisExpression);
      }
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

  private void doMoveField(PsiSubstitutor substitutor, GrMemberInfo info) {
    GroovyPsiElementFactory elementFactory = GroovyPsiElementFactory.getInstance(myProject);
    GrField field = (GrField)info.getMember();
    field.normalizeDeclaration();
    replaceMovedMemberTypeParameters(field, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);
    //fixReferencesToStatic(field, movedMembers);
    if (myTargetSuperClass.isInterface()) {
      PsiUtil.setModifierProperty(field, PsiModifier.PUBLIC, true);
    }
    final PsiMember movedElement = (PsiMember)myTargetSuperClass.add(field);
    myMembersAfterMove.add(movedElement);
    deleteMemberWithDocComment(field);
  }

  private void doMoveClass(PsiSubstitutor substitutor, GrMemberInfo info) {
    GroovyPsiElementFactory elementFactory = GroovyPsiElementFactory.getInstance(myProject);
    GrTypeDefinition aClass = (GrTypeDefinition)info.getMember();
    if (Boolean.FALSE.equals(info.getOverrides())) {
      final GrReferenceList sourceReferenceList = info.getSourceReferenceList();
      LOG.assertTrue(sourceReferenceList != null);
      GrCodeReferenceElement ref = mySourceClass.equals(sourceReferenceList.getParent()) ?
                                   removeFromReferenceList(sourceReferenceList, aClass) :
                                   findReferenceToClass(sourceReferenceList, aClass);
      if (ref != null && !myTargetSuperClass.isInheritor(aClass, false)) {
        replaceMovedMemberTypeParameters(ref, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);
        GrReferenceList referenceList;
        if (myTargetSuperClass.isInterface()) {
          referenceList = myTargetSuperClass.getExtendsClause();
          if (referenceList == null) {
            GrExtendsClause newClause = GroovyPsiElementFactory.getInstance(myProject).createExtendsClause();
            PsiElement anchor = myTargetSuperClass.getTypeParameterList() != null ? myTargetSuperClass.getTypeParameterList():
                                myTargetSuperClass.getNameIdentifierGroovy();
            referenceList = (GrReferenceList)myTargetSuperClass.addAfter(newClause, anchor);
            addSpacesAround(referenceList);
          }
        }
        else {
          referenceList = myTargetSuperClass.getImplementsClause();

          if (referenceList == null) {
            GrImplementsClause newClause = GroovyPsiElementFactory.getInstance(myProject).createImplementsClause();
            PsiElement anchor = myTargetSuperClass.getExtendsClause() != null ? myTargetSuperClass.getExtendsClause() :
                                myTargetSuperClass.getTypeParameterList() != null ? myTargetSuperClass.getTypeParameterList() :
                                myTargetSuperClass.getNameIdentifierGroovy();
            referenceList = (GrReferenceList)myTargetSuperClass.addAfter(newClause, anchor);
            addSpacesAround(referenceList);
          }

        }

        assert referenceList != null;
        referenceList.add(ref);
      }
    }
    else {
      replaceMovedMemberTypeParameters(aClass, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);
      //fixReferencesToStatic(aClass, movedMembers);
      PsiMember movedElement = (PsiMember)myTargetSuperClass.addAfter(aClass, null);
      //movedElement = (PsiMember)CodeStyleManager.getInstance(myProject).reformat(movedElement);
      myMembersAfterMove.add(movedElement);
      deleteMemberWithDocComment(aClass);
    }
  }

  private static void addSpacesAround(@NotNull GrReferenceList list) {
    PsiElement prev = list.getPrevSibling();
    if (!PsiImplUtil.isWhiteSpace(prev)) {
      list.getParent().getNode().addLeaf(TokenType.WHITE_SPACE, " ", list.getNode());
    }

    PsiElement next = list.getNextSibling();
    if (!PsiImplUtil.isWhiteSpace(next)) {
      list.getParent().getNode().addLeaf(TokenType.WHITE_SPACE, " ", list.getNode().getTreeNext());
    }
  }

  public static GrCodeReferenceElement findReferenceToClass(GrReferenceList refList, PsiClass aClass) {
    GrCodeReferenceElement[] refs = refList.getReferenceElements();
    for (GrCodeReferenceElement ref : refs) {
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
  public static GrCodeReferenceElement removeFromReferenceList(GrReferenceList refList, PsiClass aClass) throws IncorrectOperationException {
    GrCodeReferenceElement[] refs = refList.getReferenceElements();
    for (GrCodeReferenceElement ref : refs) {
      if (ref.isReferenceTo(aClass)) {
        GrCodeReferenceElement refCopy = (GrCodeReferenceElement)ref.copy();
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
