/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.impl.source.tree.java.ClassElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.ui.RowIcon;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl.GrDocCommentUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrWildcardTypeArgument;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyBaseElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author ilyas
 */
public abstract class GrTypeDefinitionImpl extends GroovyBaseElementImpl<GrTypeDefinitionStub> implements GrTypeDefinition {

  private volatile PsiClass[] myInnerClasses;
  private volatile List<PsiMethod> myMethods;
  private volatile GrMethod[] myGroovyMethods;
  private volatile GrMethod[] myConstructors;

  public GrTypeDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  protected GrTypeDefinitionImpl(GrTypeDefinitionStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitTypeDefinition(this);
  }

  public int getTextOffset() {
    return getNameIdentifierGroovy().getTextRange().getStartOffset();
  }

  @Nullable
  public String getQualifiedName() {
    final GrTypeDefinitionStub stub = getStub();
    if (stub != null) {
      return stub.getQualifiedName();
    }

    final PsiClass containingClass = getContainingClass();
    if (containingClass != null) {
      return containingClass.getQualifiedName() + "." + getName();
    }

    PsiElement parent = getParent();
    if (parent instanceof GroovyFile) {
      String packageName = ((GroovyFile)parent).getPackageName();
      return packageName.length() > 0 ? packageName + "." + getName() : getName();
    }

    return null;
  }

  public GrWildcardTypeArgument[] getTypeParametersGroovy() {
    return findChildrenByClass(GrWildcardTypeArgument.class);
  }

  @Nullable
  public GrTypeDefinitionBody getBody() {
    return (GrTypeDefinitionBody)findChildByType(GroovyElementTypes.CLASS_BODY);
  }

  @NotNull
  public GrMembersDeclaration[] getMemberDeclarations() {
    GrTypeDefinitionBody body = getBody();
    if (body == null) return GrMembersDeclaration.EMPTY_ARRAY;
    return body.getMemberDeclarations();
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      @Nullable
      public String getPresentableText() {
        return getName();
      }

      @Nullable
      public String getLocationString() {
        PsiFile file = getContainingFile();
        if (file instanceof GroovyFile) {
          GroovyFile groovyFile = (GroovyFile)file;

          return groovyFile.getPackageName().length() > 0 ? "(" + groovyFile.getPackageName() + ")" : "";
        }
        return "";
      }

      @Nullable
      public Icon getIcon(boolean open) {
        return GrTypeDefinitionImpl.this.getIcon(ICON_FLAG_VISIBILITY | ICON_FLAG_READ_STATUS);
      }

      @Nullable
      public TextAttributesKey getTextAttributesKey() {
        return null;
      }
    };
  }

  @Nullable
  public GrExtendsClause getExtendsClause() {
    return (GrExtendsClause)findChildByType(GroovyElementTypes.EXTENDS_CLAUSE);
  }

  @Nullable
  public GrImplementsClause getImplementsClause() {
    return (GrImplementsClause)findChildByType(GroovyElementTypes.IMPLEMENTS_CLAUSE);
  }

  public String[] getSuperClassNames() {
    final GrTypeDefinitionStub stub = getStub();
    if (stub != null) {
      return stub.getSuperClassNames();
    }
    return ArrayUtil.mergeArrays(getExtendsNames(), getImplementsNames(), String.class);
  }

  protected String[] getImplementsNames() {
    GrImplementsClause implementsClause = getImplementsClause();
    GrCodeReferenceElement[] implementsRefs =
      implementsClause != null ? implementsClause.getReferenceElements() : GrCodeReferenceElement.EMPTY_ARRAY;
    ArrayList<String> implementsNames = new ArrayList<String>(implementsRefs.length);
    for (GrCodeReferenceElement ref : implementsRefs) {
      String name = ref.getReferenceName();
      if (name != null) implementsNames.add(name);
    }

    return ArrayUtil.toStringArray(implementsNames);
  }

  protected String[] getExtendsNames() {
    GrExtendsClause extendsClause = getExtendsClause();
    GrCodeReferenceElement[] extendsRefs =
      extendsClause != null ? extendsClause.getReferenceElements() : GrCodeReferenceElement.EMPTY_ARRAY;
    ArrayList<String> extendsNames = new ArrayList<String>(extendsRefs.length);
    for (GrCodeReferenceElement ref : extendsRefs) {
      String name = ref.getReferenceName();
      if (name != null) extendsNames.add(name);
    }
    return ArrayUtil.toStringArray(extendsNames);
  }

  @NotNull
  public PsiElement getNameIdentifierGroovy() {
    PsiElement result = findChildByType(TokenSets.PROPERTY_NAMES);
    assert result != null;
    return result;
  }

  public void checkDelete() throws IncorrectOperationException {
  }

  public void delete() throws IncorrectOperationException {
    PsiElement parent = getParent();
    if (parent instanceof GroovyFileImpl) {
      GroovyFileImpl file = (GroovyFileImpl)parent;
      if (file.getTypeDefinitions().length == 1 && !file.isScript()) {
        file.delete();
        return;
      }
    }

    ASTNode astNode = parent.getNode();
    if (astNode != null) {
      astNode.removeChild(getNode());
    }
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return GrClassImplUtil.processDeclarations(this, processor, state, lastParent, place);
  }

  public String getName() {
    final GrTypeDefinitionStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    return PsiImplUtil.getName(this);
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return GrClassImplUtil.isClassEquivalentTo(this, another);
  }

  public boolean isInterface() {
    return false;
  }

  public boolean isAnnotationType() {
    return false;
  }

  public boolean isEnum() {
    return false;
  }

  @Nullable
  public PsiReferenceList getExtendsList() {
    return null;
  }

  @Nullable
  public PsiReferenceList getImplementsList() {
    return null;
  }

  @NotNull
  public PsiClassType[] getExtendsListTypes() {
    return GrClassImplUtil.getExtendsListTypes(this);
  }

  @NotNull
  public PsiClassType[] getImplementsListTypes() {
    return GrClassImplUtil.getImplementsListTypes(this);
  }

  @Nullable
  public PsiClass getSuperClass() {
    return GrClassImplUtil.getSuperClass(this);
  }

  public PsiClass[] getInterfaces() {
    return GrClassImplUtil.getInterfaces(this);
  }

  @NotNull
  public final PsiClass[] getSupers() {
    return GrClassImplUtil.getSupers(this);
  }

  @NotNull
  public PsiClassType[] getSuperTypes() {
    return GrClassImplUtil.getSuperTypes(this);
  }

  @NotNull
  public GrField[] getFields() {
    GrTypeDefinitionBody body = getBody();
    if (body != null) {
      return body.getFields();
    }

    return GrField.EMPTY_ARRAY;
  }

  @NotNull
  public GrClassInitializer[] getInitializersGroovy() {
    GrTypeDefinitionBody body = getBody();
    if (body != null) {
      return body.getInitializers();
    }

    return GrClassInitializer.EMPTY_ARRAY;
  }

  @NotNull
  public PsiMethod[] getMethods() {
    List<PsiMethod> cached = myMethods;
    if (cached == null) {
      cached = new ArrayList<PsiMethod>();
      GrTypeDefinitionBody body = getBody();
      if (body != null) {
        cached.addAll(body.getMethods());
      }

      myMethods = cached;
    }

    List<PsiMethod> result = new ArrayList<PsiMethod>(cached);
    GrClassImplUtil.addGroovyObjectMethods(this, result);
    return result.toArray(new PsiMethod[result.size()]);
  }

  @NotNull
  public GrMethod[] getGroovyMethods() {
    GrMethod[] cached = myGroovyMethods;
    if (cached == null) {
      GrTypeDefinitionBody body = getBody();
      myGroovyMethods = cached = body != null ? body.getGroovyMethods() : GrMethod.EMPTY_ARRAY;
    }
    return cached;
  }

  public void subtreeChanged() {
    myMethods = null;
    myInnerClasses = null;
    myConstructors = null;
    myGroovyMethods = null;
    super.subtreeChanged();
  }

  @NotNull
  public PsiMethod[] getConstructors() {
    GrMethod[] cached = myConstructors;
    if (cached == null) {
      List<GrMethod> result = new ArrayList<GrMethod>();
      for (final PsiMethod method : getMethods()) {
        if (method.isConstructor()) {
          result.add((GrMethod)method);
        }
      }

      myConstructors = cached = result.toArray(new GrMethod[result.size()]);
    }
    return cached;
  }

  @NotNull
  public PsiClass[] getInnerClasses() {
    PsiClass[] inners = myInnerClasses;
    if (inners == null) {
      final GrTypeDefinitionBody body = getBody();
      myInnerClasses = inners = body != null ? body.getInnerClasses() : PsiClass.EMPTY_ARRAY;
    }

    return inners;
  }

  @NotNull
  public PsiClassInitializer[] getInitializers() {
    return PsiClassInitializer.EMPTY_ARRAY;
  }

  @NotNull
  public PsiField[] getAllFields() {
    return GrClassImplUtil.getAllFields(this);
  }

  @NotNull
  public PsiMethod[] getAllMethods() {
    return GrClassImplUtil.getAllMethods(this);
  }

  @NotNull
  public PsiClass[] getAllInnerClasses() {
    return PsiClassImplUtil.getAllInnerClasses(this);
  }

  @Nullable
  public PsiField findFieldByName(String name, boolean checkBases) {
    return GrClassImplUtil.findFieldByName(this, name, checkBases);
  }

  @Nullable
  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    return GrClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @NotNull
  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return GrClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  @NotNull
  public PsiMethod[] findCodeMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return GrClassImplUtil.findCodeMethodsBySignature(this, patternMethod, checkBases);
  }

  @NotNull
  public PsiMethod[] findMethodsByName(@NonNls String name, boolean checkBases) {
    return GrClassImplUtil.findMethodsByName(this, name, checkBases);
  }

  @NotNull
  public PsiMethod[] findCodeMethodsByName(@NonNls String name, boolean checkBases) {
    return GrClassImplUtil.findCodeMethodsByName(this, name, checkBases);
  }

  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(String name, boolean checkBases) {
    return GrClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return GrClassImplUtil.getAllMethodsAndTheirSubstitutors(this);
  }

  @Nullable
  public PsiClass findInnerClassByName(String name, boolean checkBases) {
    return null;
  }

  @Nullable
  public PsiJavaToken getLBrace() {
    return null;
  }

  @Nullable
  public PsiJavaToken getRBrace() {
    return null;
  }

  @Nullable
  public PsiElement getLBraceGroovy() {
    GrTypeDefinitionBody body = getBody();
    if (body == null) return null;
    return body.getLBrace();
  }

  @Nullable
  public PsiElement getRBraceGroovy() {
    GrTypeDefinitionBody body = getBody();
    if (body == null) return null;
    return body.getRBrace();
  }

  public boolean isAnonymous() {
    return false;
  }

  @Nullable
  public PsiIdentifier getNameIdentifier() {
    return PsiUtil.getJavaNameIdentifier(this);
  }

  @Nullable
  public PsiElement getScope() {
    return null;
  }

  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  public boolean isInheritorDeep(PsiClass baseClass, @Nullable PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  @Nullable
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
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return PsiSuperMethodImplUtil.getVisibleSignatures(this);
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    boolean renameFile = isRenameFileOnClassRenaming();

    PsiImplUtil.setName(name, getNameIdentifierGroovy());

    if (renameFile) {
      final GroovyFileBase file = (GroovyFileBase)getContainingFile();
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
  public GrModifierList getModifierList() {
    return (GrModifierList)findChildByType(GroovyElementTypes.MODIFIERS);
  }

  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    PsiModifierList modifierList = getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(name);
  }

  @Nullable
  public GrDocComment getDocComment() {
    return GrDocCommentUtil.findDocComment(this);
  }

  public boolean isDeprecated() {
    return com.intellij.psi.impl.PsiImplUtil.isDeprecatedByDocTag(this) || com.intellij.psi.impl.PsiImplUtil.isDeprecatedByAnnotation(this);
  }

  public boolean hasTypeParameters() {
    return getTypeParameters().length > 0;
  }

  @Nullable
  public GrTypeParameterList getTypeParameterList() {
    return findChildByClass(GrTypeParameterList.class);
  }

  @NotNull
  public GrTypeParameter[] getTypeParameters() {
    final GrTypeParameterList list = getTypeParameterList();
    if (list != null) {
      return list.getTypeParameters();
    }

    return GrTypeParameter.EMPTY_ARRAY;
  }

  @Nullable
  public Icon getIcon(int flags) {
    Icon icon = getIconInner();
    final boolean isLocked = (flags & ICON_FLAG_READ_STATUS) != 0 && !isWritable();
    RowIcon rowIcon = ElementBase.createLayeredIcon(icon, ElementPresentationUtil.getFlags(this, isLocked));
    if ((flags & ICON_FLAG_VISIBILITY) != 0) {
      VisibilityIcons.setVisibilityIcon(getModifierList(), rowIcon);
    }
    return rowIcon;
  }

  private Icon getIconInner() {
    if (isAnnotationType()) return GroovyIcons.ANNOTATION_TYPE;

    if (isInterface()) return GroovyIcons.INTERFACE;

    if (isEnum()) return GroovyIcons.ENUM;

    if (hasModifierProperty(PsiModifier.ABSTRACT)) return GroovyIcons.ABSTRACT_CLASS;

    return GroovyIcons.CLASS;
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
  public PsiElement getOriginalElement() {
    return PsiImplUtil.getOriginalElement(this, getContainingFile());
  }

  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    if (anchor == null) {
      return add(element);
    }
    final GrTypeDefinitionBody body = getBody();
    assert anchor.getParent() == body;

    final PsiElement nextChild = anchor.getNextSibling();
    if (nextChild == null) {
      add(element);
      return element;
    }

    ASTNode node = element.getNode();
    assert node != null;
    //body.getNode().addLeaf(GroovyElementTypes.mNLS, "\n", nextChild.getNode());
    return body.addBefore(element, nextChild);
  }

  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    if (anchor == null) {
      add(element);
      return element;
    }

    final GrTypeDefinitionBody body = getBody();
    assert anchor.getParent() == body;

    ASTNode node = element.getNode();
    assert node != null;
    final ASTNode bodyNode = body.getNode();
    final ASTNode anchorNode = anchor.getNode();
    bodyNode.addChild(node, anchorNode);
    bodyNode.addLeaf(GroovyTokenTypes.mWS, " ", node);
    bodyNode.addLeaf(GroovyTokenTypes.mNLS, "\n", anchorNode);
    return element;
  }

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
      if (GroovyElementTypes.mSEMI.equals(node.getElementType())) {
        anchor = anchor.getNextSibling();
      }
      psiElement = body.addBefore(psiElement, anchor);
    }
    else {
      body.add(psiElement);
    }

    return psiElement;
  }

  @Nullable
  private static PsiMember getAnyMember(@Nullable PsiElement psiElement) {
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

  @Nullable
  private PsiElement getDefaultAnchor(GrTypeDefinitionBody body, PsiMember member) {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());

    int order = ClassElement.getMemberOrderWeight(member, settings);
    if (order < 0) return null;

    PsiElement lastMember = null;
    for (PsiElement child = body.getFirstChild(); child != null; child = child.getNextSibling()) {
      int order1 = ClassElement.getMemberOrderWeight(getAnyMember(child), settings);
      if (order1 < 0) continue;
      if (order1 > order) {
        final PsiElement lBrace = body.getLBrace();
        if (lastMember != null) {
          PsiElement nextSibling = lastMember.getNextSibling();
          while (nextSibling instanceof LeafPsiElement && (nextSibling.getText().equals(",") || nextSibling.getText().equals(";"))) {
            nextSibling = nextSibling.getNextSibling();
          }
          return nextSibling == null && lBrace != null ? PsiUtil.skipWhitespaces(lBrace.getNextSibling(), true) : nextSibling;
        }
        else if (lBrace != null) {
          return PsiUtil.skipWhitespaces(lBrace.getNextSibling(), true);
        }
      }
      lastMember = child;
    }
    return body.getRBrace();
  }


  public <T extends GrMembersDeclaration> T addMemberDeclaration(@NotNull T decl, PsiElement anchorBefore)
    throws IncorrectOperationException {

    if (anchorBefore == null) {
      return (T)add(decl);
    }

    GrTypeDefinitionBody body = getBody();
    if (body == null) throw new IncorrectOperationException("Type definition without a body");
//    ASTNode anchorNode;
//    anchorNode = anchorBefore.getNode();
//    ASTNode bodyNode = body.getNode();
    decl = (T)body.addBefore(decl, anchorBefore);
//    bodyNode.addLeaf(GroovyTokenTypes.mWS, " ", decl.getNode()); //add whitespaces before and after to hack over incorrect auto reformat
//    bodyNode.addLeaf(GroovyTokenTypes.mWS, " ", anchorNode);
    return decl;
  }

}
