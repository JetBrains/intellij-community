// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityIcons;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl.GrDocCommentUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyEmptyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyCodeStyleSettingsFacade;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierListUtil;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyRunnerPsiUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

/**
 * @author ilyas
 */
public abstract class GrTypeDefinitionImpl extends GrStubElementBase<GrTypeDefinitionStub>
  implements GrTypeDefinition, StubBasedPsiElement<GrTypeDefinitionStub> {

  private final GrTypeDefinitionMembersCache<GrTypeDefinition> myCache = new GrTypeDefinitionMembersCache<>(this);

  public GrTypeDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  protected GrTypeDefinitionImpl(GrTypeDefinitionStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitTypeDefinition(this);
  }

  @Override
  public int getTextOffset() {
    return getNameIdentifierGroovy().getTextRange().getStartOffset();
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    final GrTypeDefinitionStub stub = getStub();
    if (stub != null) {
      return stub.getQualifiedName();
    }

    PsiElement parent = getParent();
    if (parent instanceof GroovyFile) {
      String packageName = ((GroovyFile)parent).getPackageName();
      return !packageName.isEmpty() ? packageName + "." + getName() : getName();
    }

    final PsiClass containingClass = getContainingClass();
    if (containingClass != null && containingClass.getQualifiedName() != null) {
      return containingClass.getQualifiedName() + "." + getName();
    }

    return null;
  }

  @Nullable
  @Override
  public GrTypeDefinitionBody getBody() {
    return getStubOrPsiChild(GroovyEmptyStubElementTypes.CLASS_BODY);
  }

  @Override
  public GrMembersDeclaration @NotNull [] getMemberDeclarations() {
    GrTypeDefinitionBody body = getBody();
    if (body == null) return GrMembersDeclaration.EMPTY_ARRAY;
    return body.getMemberDeclarations();
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @Nullable
  @Override
  public GrExtendsClause getExtendsClause() {
    return getStubOrPsiChild(GroovyStubElementTypes.EXTENDS_CLAUSE);
  }

  @Nullable
  @Override
  public GrImplementsClause getImplementsClause() {
    return getStubOrPsiChild(GroovyStubElementTypes.IMPLEMENTS_CLAUSE);
  }

  @Override
  public @Nullable GrPermitsClause getPermitsClause() {
    return getStubOrPsiChild(GroovyStubElementTypes.PERMITS_CLAUSE);
  }

  @Override
  public @Nullable PsiReferenceList getPermitsList() {
    return getPermitsClause();
  }

  @Override
  public PsiClassType @NotNull [] getPermitsListTypes() {
    GrPermitsClause permitsClause = getPermitsClause();
    return permitsClause == null ? PsiClassType.EMPTY_ARRAY : permitsClause.getReferencedTypes();
  }

  @Override
  @NotNull
  public PsiElement getNameIdentifierGroovy() {
    PsiElement result = findChildByType(TokenSets.PROPERTY_NAMES);
    assert result != null;
    return result;
  }

  @Override
  public void delete() throws IncorrectOperationException {
    PsiElement parent = getParent();
    if (parent instanceof GroovyFileImpl) {
      GroovyFileImpl file = (GroovyFileImpl)parent;
      if (file.getTypeDefinitions().length == 1 && !file.isScript()) {
        file.delete();
        return;
      }
    }

    super.delete();
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return GrClassImplUtil.processDeclarations(this, processor, state, lastParent, place);
  }

  @Override
  public String getName() {
    final GrTypeDefinitionStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    return org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil.getName(this);
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return GrClassImplUtil.isClassEquivalentTo(this, another);
  }

  @Override
  public boolean isInterface() {
    return false;
  }

  @Override
  public boolean isAnnotationType() {
    return false;
  }

  @Override
  public boolean isEnum() {
    return false;
  }

  @Override
  public boolean isTrait() {
    return false;
  }

  @Nullable
  @Override
  public PsiReferenceList getExtendsList() {
    //return PsiImplUtil.getOrCreatePsiReferenceList(getExtendsClause(), PsiReferenceList.Role.EXTENDS_LIST);
    return getExtendsClause();
  }

  @Nullable
  @Override
  public PsiReferenceList getImplementsList() {
    //return PsiImplUtil.getOrCreatePsiReferenceList(getImplementsClause(), PsiReferenceList.Role.IMPLEMENTS_LIST);
    return getImplementsClause();
  }

  @Override
  public PsiClassType @NotNull [] getExtendsListTypes(boolean includeSynthetic) {
    return myCache.getExtendsListTypes(includeSynthetic);
  }

  @Override
  public PsiClassType @NotNull [] getImplementsListTypes(boolean includeSynthetic) {
    return myCache.getImplementsListTypes(includeSynthetic);
  }

  @Nullable
  @Override
  public PsiClass getSuperClass() {
    return GrClassImplUtil.getSuperClass(this);
  }

  @Override
  public PsiClass @NotNull [] getInterfaces() {
    return GrClassImplUtil.getInterfaces(this);
  }

  @Override
  public final PsiClass @NotNull [] getSupers(boolean includeSynthetic) {
    return GrClassImplUtil.getSupers(this, includeSynthetic);
  }

  @Override
  public PsiClassType @NotNull [] getSuperTypes(boolean includeSynthetic) {
    return GrClassImplUtil.getSuperTypes(this, includeSynthetic);
  }

  @Override
  public GrField @NotNull [] getCodeFields() {
    return myCache.getCodeFields();
  }

  @Override
  public PsiField findCodeFieldByName(String name, boolean checkBases) {
    return GrClassImplUtil.findFieldByName(this, name, checkBases, false);
  }

  @Override
  public GrField @NotNull [] getFields() {
    return myCache.getFields();
  }

  @Override
  public PsiMethod @NotNull [] getMethods() {
    return myCache.getMethods();
  }

  @Override
  public GrMethod @NotNull [] getCodeMethods() {
    return myCache.getCodeMethods();
  }

  @Override
  public PsiMethod @NotNull [] getConstructors() {
    return myCache.getConstructors();
  }

  @Override
  public GrMethod @NotNull [] getCodeConstructors() {
    return myCache.getCodeConstructors();
  }

  @Override
  public PsiClass @NotNull [] getInnerClasses() {
    return myCache.getInnerClasses();
  }

  @Override
  public GrTypeDefinition @NotNull [] getCodeInnerClasses() {
    return myCache.getCodeInnerClasses();
  }

  @Override
  public GrClassInitializer @NotNull [] getInitializers() {
    GrTypeDefinitionBody body = getBody();
    return body != null ? body.getInitializers() : GrClassInitializer.EMPTY_ARRAY;
  }

  @Override
  public PsiField @NotNull [] getAllFields() {
    return GrClassImplUtil.getAllFields(this);
  }

  @Override
  public PsiMethod @NotNull [] getAllMethods() {
    return GrClassImplUtil.getAllMethods(this);
  }

  @Override
  public PsiClass @NotNull [] getAllInnerClasses() {
    return PsiClassImplUtil.getAllInnerClasses(this);
  }

  @Nullable
  @Override
  public PsiField findFieldByName(String name, boolean checkBases) {
    return GrClassImplUtil.findFieldByName(this, name, checkBases, true);
  }

  @Nullable
  @Override
  public PsiMethod findMethodBySignature(@NotNull PsiMethod patternMethod, boolean checkBases) {
    return GrClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @Override
  public PsiMethod @NotNull [] findMethodsBySignature(@NotNull PsiMethod patternMethod, boolean checkBases) {
    return GrClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  @Override
  public PsiMethod @NotNull [] findCodeMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return GrClassImplUtil.findCodeMethodsBySignature(this, patternMethod, checkBases);
  }

  @Override
  public PsiMethod @NotNull [] findMethodsByName(@NonNls String name, boolean checkBases) {
    return GrClassImplUtil.findMethodsByName(this, name, checkBases);
  }

  @Override
  public PsiMethod @NotNull [] findCodeMethodsByName(@NonNls String name, boolean checkBases) {
    return GrClassImplUtil.findCodeMethodsByName(this, name, checkBases);
  }

  @NotNull
  @Override
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NotNull String name, boolean checkBases) {
    return GrClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @NotNull
  @Override
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return GrClassImplUtil.getAllMethodsAndTheirSubstitutors(this);
  }

  @Nullable
  @Override
  public PsiClass findInnerClassByName(String name, boolean checkBases) {
    return GrClassImplUtil.findInnerClassByName(this, name, checkBases);
  }

  @Nullable
  @Override
  public PsiElement getLBrace() {
    final GrTypeDefinitionBody body = getBody();
    return body == null ? null : body.getLBrace();
  }

  @Nullable
  @Override
  public PsiElement getRBrace() {
    final GrTypeDefinitionBody body = getBody();
    return body == null ? null : body.getRBrace();
  }

  @Override
  public boolean isAnonymous() {
    return false;
  }

  @Nullable
  @Override
  public PsiIdentifier getNameIdentifier() {
    return PsiUtil.getJavaNameIdentifier(this);
  }

  @Nullable
  @Override
  public PsiElement getScope() {
    final GrTypeDefinitionStub stub = getStub();
    if (stub != null) {
      return stub.getParentStub().getPsi();
    }

    ASTNode treeElement = getNode();
    ASTNode parent = treeElement.getTreeParent();

    while (parent != null) {
      if (parent.getElementType() instanceof IStubElementType && !(parent.getElementType() == GroovyEmptyStubElementTypes.CLASS_BODY)) {
        return parent.getPsi();
      }
      parent = parent.getTreeParent();
    }

    return getContainingFile();
  }

  @Override
  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    if (isTrait() && baseClass.isInterface() && !checkDeep) {
      for (PsiClassType superType : getImplementsListTypes()) {
        if (getManager().areElementsEquivalent(superType.resolve(), baseClass)) {
          return true;
        }
      }
    }
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  @Override
  public boolean isInheritorDeep(@NotNull PsiClass baseClass, @Nullable PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  @Nullable
  @Override
  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    if (parent instanceof GrTypeDefinitionBody) {
      final PsiElement pparent = parent.getParent();
      if (pparent instanceof PsiClass) {
        return (PsiClass)pparent;
      }
    }

    return null;
  }

  @NotNull
  @Override
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return PsiSuperMethodImplUtil.getVisibleSignatures(this);
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    boolean renameFile = isRenameFileOnClassRenaming();

    final String oldName = getName();
    org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil.setName(name, getNameIdentifierGroovy());

    final GrTypeDefinitionBody body = getBody();
    if (body != null) {
      for (PsiMethod method : body.getMethods()) {
        if (method.isConstructor() && method.getName().equals(oldName)) method.setName(name);
      }
    }

    if (renameFile) {
      final PsiFile file = getContainingFile();
      final VirtualFile virtualFile = file.getVirtualFile();
      final String ext;
      if (virtualFile != null) {
        ext = virtualFile.getExtension();
      } else {
        ext = GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension();
      }
      file.setName(name + "." + ext);
    }

    return this;
  }

  @Nullable
  @Override
  public GrModifierList getModifierList() {
    return getStubOrPsiChild(GroovyStubElementTypes.MODIFIER_LIST);
  }

  @Override
  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    GrModifierList modifierList = getModifierList();
    return modifierList != null && GrModifierListUtil.hasModifierProperty(modifierList, name, false);
  }

  @Nullable
  @Override
  public GrDocComment getDocComment() {
    return GrDocCommentUtil.findDocComment(this);
  }

  @Override
  public boolean isDeprecated() {
    final GrTypeDefinitionStub stub = getStub();
    if (stub != null) {
      return stub.isDeprecatedByDoc() || PsiImplUtil.isDeprecatedByAnnotation(this);
    }
    return PsiImplUtil.isDeprecatedByDocTag(this) || PsiImplUtil.isDeprecatedByAnnotation(this);
  }

  @Override
  public boolean hasTypeParameters() {
    return getTypeParameters().length > 0;
  }

  @Nullable
  @Override
  public GrTypeParameterList getTypeParameterList() {
    return getStubOrPsiChild(GroovyEmptyStubElementTypes.TYPE_PARAMETER_LIST);
  }

  @Override
  public GrTypeParameter @NotNull [] getTypeParameters() {
    final GrTypeParameterList list = getTypeParameterList();
    if (list != null) {
      return list.getTypeParameters();
    }

    return GrTypeParameter.EMPTY_ARRAY;
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

  @Nullable
  @Override
  protected Icon getElementIcon(@IconFlags int flags) {
    Icon icon = getIconInner();
    final boolean isLocked = (flags & ICON_FLAG_READ_STATUS) != 0 && !isWritable();
    RowIcon rowIcon = IconManager.getInstance()
      .createLayeredIcon(this, icon, ElementPresentationUtil.getFlags(this, isLocked) | getFlagsInner());
    if ((flags & ICON_FLAG_VISIBILITY) != 0) {
      VisibilityIcons.setVisibilityIcon(getModifierList(), rowIcon);
    }
    return rowIcon;
  }

  //hack to get runnable icon for all classes that can be run by Groovy
  private int getFlagsInner() {
    return !DumbService.isDumb(getProject()) && GroovyRunnerPsiUtil.isRunnable(this) ? ElementPresentationUtil.FLAGS_RUNNABLE : 0;
  }

  private Icon getIconInner() {
    if (isAnnotationType()) return JetgroovyIcons.Groovy.AnnotationType;

    if (isTrait()) return JetgroovyIcons.Groovy.Trait;

    if (isInterface()) return JetgroovyIcons.Groovy.Interface;

    if (isEnum()) return JetgroovyIcons.Groovy.Enum;

    if (hasModifierProperty(PsiModifier.ABSTRACT)) return JetgroovyIcons.Groovy.AbstractClass;

    return JetgroovyIcons.Groovy.Class;
  }

  private boolean isRenameFileOnClassRenaming() {
    final PsiFile file = getContainingFile();
    if (!(file instanceof GroovyFile)) return false;
    final GroovyFile groovyFile = (GroovyFile)file;
    if (groovyFile.isScript()) return false;
    final String name = getName();
    final VirtualFile vFile = groovyFile.getVirtualFile();
    return vFile != null && name != null && name.equals(vFile.getNameWithoutExtension());
  }

  @Nullable
  @Override
  public PsiElement getOriginalElement() {
    return JavaPsiImplementationHelper.getInstance(getProject()).getOriginalClass(this);
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    if (anchor == null) {
      return add(element);
    }
    final GrTypeDefinitionBody body = getBody();
    if (anchor.getParent() == body) {

      final PsiElement nextChild = anchor.getNextSibling();
      if (nextChild == null) {
        return add(element);
      }

      if (body == null) throw new IncorrectOperationException("Class must have body");
      return body.addBefore(element, nextChild);
    }
    else {
      return super.addAfter(element, anchor);
    }
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    if (anchor == null) {
      return add(element);
    }

    final GrTypeDefinitionBody body = getBody();
    if (anchor.getParent() != body) {
      return super.addBefore(element, anchor);
    }

    if (body == null) throw new IncorrectOperationException("Class must have body");
    return body.addBefore(element, anchor);
  }

  @Override
  public PsiElement add(@NotNull PsiElement psiElement) throws IncorrectOperationException {
    final GrTypeDefinitionBody body = getBody();

    if (body == null) throw new IncorrectOperationException("Class must have body");

    final PsiElement lBrace = body.getLBrace();

    if (lBrace == null) throw new IncorrectOperationException("No left brace");

    PsiMember member = getAnyMember(psiElement);
    PsiElement anchor = member != null ? getDefaultAnchor(body, member) : null;
    if (anchor == null) {
      anchor = lBrace.getNextSibling();
    }

    if (anchor != null) {
      ASTNode node = anchor.getNode();
      assert node != null;
      if (GroovyTokenTypes.mSEMI.equals(node.getElementType())) {
        anchor = anchor.getNextSibling();
      }
      if (psiElement instanceof GrField) {
        //add field with modifiers which are in its parent
        int i = ArrayUtilRt.find(((GrVariableDeclaration)psiElement.getParent()).getVariables(), psiElement);
        psiElement = body.addBefore(psiElement.getParent(), anchor);
        GrVariable[] vars = ((GrVariableDeclaration)psiElement).getVariables();
        for (int j = 0; j < vars.length; j++) {
          if (i != j) vars[i].delete();
        }
        psiElement = vars[i];
      }
      else {
        psiElement = body.addBefore(psiElement, anchor);
      }
    }
    else {
      psiElement = body.add(psiElement);
    }

    return psiElement;
  }

  @Nullable
  protected static PsiMember getAnyMember(@Nullable PsiElement psiElement) {
    if (psiElement instanceof PsiMember) {
      return (PsiMember)psiElement;
    }
    if (psiElement instanceof GrVariableDeclaration) {
      final GrMember[] members = ((GrVariableDeclaration)psiElement).getMembers();
      if (members.length > 0) {
        return members[0];
      }
    }
    return null;
  }

  // TODO remove as soon as an arrangement sub-system is provided for groovy.
  public static int getMemberOrderWeight(PsiElement member, GroovyCodeStyleSettingsFacade settings) {
    if (member instanceof PsiField) {
      if (member instanceof PsiEnumConstant) {
        return 1;
      }
      return ((PsiField)member).hasModifierProperty(PsiModifier.STATIC) ? settings.staticFieldsOrderWeight() + 1
                                                                        : settings.fieldsOrderWeight() + 1;
    }
    if (member instanceof PsiMethod) {
      if (((PsiMethod)member).isConstructor()) {
        return settings.constructorsOrderWeight() + 1;
      }
      return ((PsiMethod)member).hasModifierProperty(PsiModifier.STATIC) ? settings.staticMethodsOrderWeight() + 1
                                                                         : settings.methodsOrderWeight() + 1;
    }
    if (member instanceof PsiClass) {
      return ((PsiClass)member).hasModifierProperty(PsiModifier.STATIC) ? settings.staticInnerClassesOrderWeight() + 1
                                                                        : settings.innerClassesOrderWeight() + 1;
    }
    return -1;
  }

  @NotNull public List<String> getSyntheticModifiers(@NotNull GrModifierList modifierList) {
    return myCache.getSyntheticModifiers(modifierList);
  }

  @Nullable
  protected PsiElement getDefaultAnchor(GrTypeDefinitionBody body, PsiMember member) {
    GroovyCodeStyleSettingsFacade settings = GroovyCodeStyleSettingsFacade.getInstance(getProject());

    int order = getMemberOrderWeight(member, settings);
    if (order < 0) return null;

    PsiElement lastMember = null;
    for (PsiElement child = body.getFirstChild(); child != null; child = child.getNextSibling()) {
      int order1 = getMemberOrderWeight(getAnyMember(child), settings);
      if (order1 < 0) continue;
      if (order1 > order) {
        final PsiElement lBrace = body.getLBrace();
        if (lastMember != null) {
          PsiElement nextSibling = lastMember.getNextSibling();
          while (nextSibling instanceof LeafPsiElement && (nextSibling.getText().equals(",") || nextSibling.getText().equals(";"))) {
            nextSibling = nextSibling.getNextSibling();
          }
          return nextSibling == null && lBrace != null ? PsiUtil.skipWhitespacesAndComments(lBrace.getNextSibling(), true) : nextSibling;
        }
        else if (lBrace != null) {
          return PsiUtil.skipWhitespacesAndComments(lBrace.getNextSibling(), true);
        }
      }
      lastMember = child;
    }
    return body.getRBrace();
  }
}
