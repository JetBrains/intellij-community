// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.light.*;
import com.intellij.psi.presentation.java.JavaPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import javax.swing.*;
import java.util.*;

import static org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier.GrModifierConstant;

public class GrLightMethodBuilder extends LightElement implements GrMethod, OriginInfoAwareElement {

  public static final Key<String> KIND_KEY = Key.create("GrLightMethodBuilder.Key");

  protected @NlsSafe String myName;
  private PsiType myReturnType = PsiTypes.voidType();
  private final GrLightModifierList myModifierList;
  private final GrLightParameterListBuilder myParameterList;
  private final LightTypeParameterListBuilder myTypeParameterList;
  private final LightReferenceListBuilder myThrowsList;
  private boolean myConstructor = false;
  private PsiClass myContainingClass;
  private Map<String, NamedArgumentDescriptor> myNamedParameters = Collections.emptyMap();

  private Icon myBaseIcon;
  private Object myMethodKind;
  private Object myData;
  private String myOriginInfo;

  public GrLightMethodBuilder(GrTypeDefinition constructedClass) {
    this(constructedClass.getManager(), constructedClass.getName());
    setConstructor(true);
    setContainingClass(constructedClass);
    setNavigationElement(constructedClass);
  }

  public GrLightMethodBuilder(PsiManager manager, @NlsSafe String name) {
    super(manager, GroovyLanguage.INSTANCE);
    myName = name;
    myModifierList = new GrLightModifierList(this);
    myParameterList = new GrLightParameterListBuilder(manager, GroovyLanguage.INSTANCE);
    myTypeParameterList = new LightTypeParameterListBuilder(manager, GroovyLanguage.INSTANCE);
    myThrowsList = new LightReferenceListBuilder(manager, GroovyLanguage.INSTANCE, PsiReferenceList.Role.THROWS_LIST);
  }

  public GrLightMethodBuilder setNamedParameters(@NotNull Map<String, NamedArgumentDescriptor> namedParameters) {
    myNamedParameters = namedParameters;
    return this;
  }

  @Override
  public ItemPresentation getPresentation() {
    return JavaPresentationUtil.getMethodPresentation(this);
  }

  @Override
  public boolean hasTypeParameters() {
    return getTypeParameters().length != 0;
  }

  @Override
  public PsiTypeParameter @NotNull [] getTypeParameters() {
    return getTypeParameterList().getTypeParameters();
  }

  @Override
  @NotNull
  public LightTypeParameterListBuilder getTypeParameterList() {
    return myTypeParameterList;
  }

  @Override
  public GrDocComment getDocComment() {
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return false;
  }

  @Override
  public PsiElement setName(@NlsSafe @NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Please don't rename light methods");
  }

  @Override
  public @NlsSafe @NotNull String getName() {
    return myName;
  }

  @Override
  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
  }

  @Override
  public boolean hasModifierProperty(@GrModifierConstant @NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @Override
  public GrMember[] getMembers() {
    return GrMember.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public GrLightModifierList getModifierList() {
    return myModifierList;
  }

  @NotNull
  @Override
  public Map<String, NamedArgumentDescriptor> getNamedParameters() {
    return myNamedParameters;
  }

  @Override
  public GrReflectedMethod @NotNull [] getReflectedMethods() {
    // not caching
    return GrReflectedMethodImpl.doCreateReflectedMethods(this, null, getParameters());
  }

  public GrLightMethodBuilder addModifier(@GrModifierConstant String modifier) {
    getModifierList().addModifier(modifier);
    return this;
  }

  public GrLightMethodBuilder addModifier(int modifier) {
    getModifierList().addModifier(modifier);
    return this;
  }

  public GrLightMethodBuilder setModifiers(@GrModifierConstant String[] modifiers) {
    getModifierList().setModifiers(modifiers);
    return this;
  }

  public GrLightMethodBuilder setModifiers(int modifiers) {
    getModifierList().setModifiers(modifiers);
    return this;
  }

  @Override
  public GrOpenBlock getBlock() {
    return null;
  }

  @Override
  public void setBlock(GrCodeBlock newBlock) {
    throw new IncorrectOperationException();
  }

  @Override
  public GrTypeElement getReturnTypeElementGroovy() {
    PsiType returnType = getReturnType();
    if (returnType == null) {
      return null;
    }
    return new GrLightTypeElement(returnType, getManager());
  }

  @Override
  public PsiType getInferredReturnType() {
    return getReturnType();
  }

  @Override
  public PsiType getReturnType() {
    return myReturnType;
  }

  @Nullable
  public GrTypeElement setReturnType(@NlsSafe String returnType, GlobalSearchScope scope) {
    setReturnType(JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory().createTypeByFQClassName(returnType, scope));
    return null;
  }

  @Override
  public GrTypeElement setReturnType(@Nullable PsiType returnType) {
    myReturnType = returnType;
    return null;
  }

  @Override
  public PsiTypeElement getReturnTypeElement() {
    return null;
  }

  @Override
  public GrParameter @NotNull [] getParameters() {
    return getParameterList().getParameters();
  }

  @Override
  @NotNull
  public GrLightParameterListBuilder getParameterList() {
    return myParameterList;
  }

  @NotNull
  public GrLightMethodBuilder addParameter(@NotNull GrParameter parameter) {
    getParameterList().addParameter(parameter);
    return this;
  }

  @NotNull
  public GrLightMethodBuilder addParameter(@NlsSafe @NotNull String name, @NlsSafe @NotNull String type) {
    return addParameter(name, type, false);
  }

  @NotNull
  public GrLightMethodBuilder addOptionalParameter(@NlsSafe @NotNull String name, @NlsSafe @NotNull String type) {
    return addParameter(name, type, true);
  }

  @NotNull
  private GrLightMethodBuilder addParameter(@NlsSafe @NotNull String name, @NlsSafe @NotNull String type, boolean isOptional) {
    return addParameter(name, TypesUtil.createType(type, ObjectUtils.notNull(getContext(), this)), isOptional);
  }

  @NotNull
  public GrLightMethodBuilder addParameter(@NlsSafe @NotNull String name, @NlsSafe @Nullable PsiType type) {
    return addParameter(name, type, false);
  }

  @NotNull
  public GrLightMethodBuilder addParameter(@NlsSafe @NotNull String name, @NlsSafe @Nullable PsiType type, boolean isOptional) {
    GrLightParameter param = new GrLightParameter(name, type, this).setOptional(isOptional);
    return addParameter(param);
  }

  @NotNull
  public GrLightParameter addAndGetParameter(@NlsSafe @NotNull String name, @NlsSafe @NotNull String type) {
    return addAndGetParameter(name, type, false);
  }

  @NotNull
  public GrLightParameter addAndGetParameter(@NlsSafe @NotNull String name, @NlsSafe @NotNull PsiType type) {
    return addAndGetParameter(name, type, false);
  }

  @NotNull
  public GrLightParameter addAndGetOptionalParameter(@NlsSafe @NotNull String name, @NlsSafe @NotNull String type) {
    return addAndGetParameter(name, type, true);
  }

  @NotNull
  private GrLightParameter addAndGetParameter(@NlsSafe @NotNull String name, @NlsSafe @NotNull String type, boolean isOptional) {
    return addAndGetParameter(name, JavaPsiFacade.getElementFactory(getProject()).createTypeFromText(type, this), isOptional);
  }

  @NotNull
  public GrLightParameter addAndGetParameter(@NlsSafe @NotNull String name, @NlsSafe @NotNull PsiType type, boolean isOptional) {
    GrLightParameter param = new GrLightParameter(name, type, this).setOptional(isOptional);
    addParameter(param);
    return param;
  }

  @Override
  @NotNull
  public LightReferenceListBuilder getThrowsList() {
    return myThrowsList;
  }

  @Override
  public PsiCodeBlock getBody() {
    return null;
  }

  @Override
  public boolean isConstructor() {
    return myConstructor;
  }

  public GrLightMethodBuilder setConstructor(boolean constructor) {
    myConstructor = constructor;
    return this;
  }

  @Override
  public boolean isVarArgs() {
    GrParameter[] parameters = getParameterList().getParameters();
    if (parameters.length == 0) return false;
    return parameters[parameters.length - 1].isVarArgs();
  }

  @Override
  @NotNull
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
    return MethodSignatureBackedByPsiMethod.create(this, substitutor);
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
  public PsiMethod @NotNull [] findSuperMethods() {
    return PsiSuperMethodImplUtil.findSuperMethods(this);
  }

  @Override
  public PsiMethod @NotNull [] findSuperMethods(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, checkAccess);
  }

  @Override
  public PsiMethod @NotNull [] findSuperMethods(PsiClass parentClass) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, parentClass);
  }

  @Override
  @NotNull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess);
  }

  @Override
  public PsiMethod findDeepestSuperMethod() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethod(this);
  }

  @Override
  public PsiMethod @NotNull [] findDeepestSuperMethods() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethods(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitMethod(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  public GrLightMethodBuilder setContainingClass(PsiClass containingClass) {
    myContainingClass = containingClass;
    return this;
  }

  public Object getMethodKind() {
    return myMethodKind;
  }

  public GrLightMethodBuilder setMethodKind(@Nullable Object methodKind) {
    myMethodKind = methodKind;
    return this;
  }

  @NotNull
  @Override
  public PsiElement getNameIdentifierGroovy() {
    return new LightIdentifier(getManager(), getName());
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
  }

  @Override
  public void acceptChildren(@NotNull GroovyElementVisitor visitor) {
  }

  public static Object getMethodKind(@Nullable PsiElement method) {
    if (method == null) return null;

    if (method instanceof GrLightMethodBuilder) {
      return ((GrLightMethodBuilder)method).getMethodKind();
    }

    return method.getUserData(KIND_KEY);
  }

  public static boolean checkKind(@Nullable PsiElement method, @NotNull Object kind) {
    return kind.equals(getMethodKind(method));
  }

  public static boolean checkKind(@Nullable PsiElement method, @NotNull Object kind1, @NotNull Object kind2) {
    Object kind = getMethodKind(method);
    return kind1.equals(kind) || kind2.equals(kind);
  }

  @Override
  public String toString() {
    return (myMethodKind == null ? "" : myMethodKind + ":") + getName();
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

  @Override
  public Icon getElementIcon(final int flags) {
    Icon methodIcon = myBaseIcon != null ? myBaseIcon :
                      hasModifierProperty(PsiModifier.ABSTRACT) ? PlatformIcons.ABSTRACT_METHOD_ICON : IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Method);
    RowIcon baseIcon = IconManager.getInstance().createLayeredIcon(this, methodIcon, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  public GrLightMethodBuilder setBaseIcon(Icon baseIcon) {
    myBaseIcon = baseIcon;
    return this;
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isMethodEquivalentTo(this, another) || getNavigationElement() == another;
  }

  @Override
  @NotNull
  public SearchScope getUseScope() {
    return PsiImplUtil.getMemberUseScope(this);
  }

  @Override
  @Nullable
  public PsiFile getContainingFile() {
    final PsiClass containingClass = getContainingClass();
    return containingClass == null ? null : containingClass.getContainingFile();
  }

  @Override
  public PsiElement getContext() {
    final PsiElement navElement = getNavigationElement();
    if (navElement != this) {
      return navElement;
    }

    final PsiClass cls = getContainingClass();
    if (cls != null) {
      return cls;
    }

    return getContainingFile();
  }

  protected void copyData(GrLightMethodBuilder dst) {
    dst.setMethodKind(getMethodKind());
    dst.setData(getData());
    dst.setNamedParameters(getNamedParameters());
    if (getNavigationElement() != this) {
      dst.setNavigationElement(getNavigationElement());
    }
    dst.setBaseIcon(myBaseIcon);
    dst.setReturnType(getReturnType());
    dst.setContainingClass(getContainingClass());

    dst.getModifierList().copyModifiers(this);

    dst.getParameterList().clear();
    for (GrParameter parameter : getParameterList().getParameters()) {
      dst.addParameter(parameter);
    }
  }

  @Override
  public GrLightMethodBuilder copy() {
    GrLightMethodBuilder copy = new GrLightMethodBuilder(myManager, myName);
    copyData(copy);
    return copy;
  }

  public <T> T getData() {
    //noinspection unchecked
    return (T)myData;
  }

  @Nullable
  public static <T> T getData(@Nullable PsiElement method, @NotNull Object kind) {
    if (method instanceof GrLightMethodBuilder) {
      if (kind.equals(((GrLightMethodBuilder)method).getMethodKind())) {
        return ((GrLightMethodBuilder)method).getData();
      }
    }
    return null;
  }

  public GrLightMethodBuilder setData(@Nullable Object data) {
    myData = data;
    return this;
  }

  public GrLightMethodBuilder addException(PsiClassType type) {
    getThrowsList().addReference(type);
    return this;
  }

  @Nullable
  @Override
  public String getOriginInfo() {
    return myOriginInfo;
  }

  public void setOriginInfo(@NonNls @Nullable String originInfo) {
    myOriginInfo = originInfo;
  }

  @NotNull
  public LightTypeParameterBuilder addTypeParameter(@NlsSafe @NotNull String name) {
    LightTypeParameterBuilder typeParameter = new LightTypeParameterBuilder(name, this, getTypeParameters().length) {
      @Override
      public PsiFile getContainingFile() {
        return GrLightMethodBuilder.this.getContainingFile();
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LightTypeParameterBuilder builder = (LightTypeParameterBuilder)o;
        return getName().equals(builder.getName());
      }

      @Override
      public int hashCode() {
        return getName().hashCode();
      }
    };
    myTypeParameterList.addParameter(typeParameter);
    return typeParameter;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GrLightMethodBuilder builder = (GrLightMethodBuilder)o;
    return myConstructor == builder.myConstructor &&
           Objects.equals(myName, builder.myName) &&
           Objects.equals(myMethodKind, builder.myMethodKind) &&
           Objects.equals(getReturnType(), builder.getReturnType()) &&
           Objects.equals(myModifierList, builder.myModifierList) &&
           Objects.equals(myParameterList, builder.myParameterList) &&
           Arrays.equals(myTypeParameterList.getTypeParameters(), builder.myTypeParameterList.getTypeParameters()) &&
           Arrays.equals(myThrowsList.getReferencedTypes(), builder.myThrowsList.getReferencedTypes()) &&
           Objects.equals(myContainingClass, builder.myContainingClass) &&
           Objects.equals(myNamedParameters, builder.myNamedParameters) &&
           Objects.equals(myData, builder.myData);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      myConstructor,
      myName,
      myMethodKind,
      getReturnType(),
      myModifierList,
      myParameterList,
      Arrays.hashCode(myTypeParameterList.getTypeParameters()),
      Arrays.hashCode(myThrowsList.getReferencedTypes()),
      myContainingClass,
      myNamedParameters,
      myData
    );
  }
}
