/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.PomMemberOwner;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.impl.InheritanceImplUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.IconUtil;
import com.intellij.ui.RowIcon;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrWildcardTypeArgument;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClassReferenceType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.DefaultGroovyMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.JavaIdentifier;
import org.jetbrains.plugins.groovy.lang.resolve.CollectClassMembersUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

import javax.swing.*;
import java.util.*;

/**
 * @author ilyas
 */
public abstract class GrTypeDefinitionImpl extends GroovyPsiElementImpl implements GrTypeDefinition {
  private PsiMethod[] myMethods;
  private GrMethod[] myGroovyMethods;
  private GrMethod[] myConstructors;

  public GrTypeDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitTypeDefinition(this);
  }

  public int getTextOffset() {
    return getNameIdentifierGroovy().getTextRange().getStartOffset();
  }

  @Nullable
  public String getQualifiedName() {
    PsiElement parent = getParent();
    if (parent instanceof GrTypeDefinition) {
      return ((GrTypeDefinition) parent).getQualifiedName() + "." + getName();
    } else if (parent instanceof GroovyFile) {
      String packageName = ((GroovyFile) parent).getPackageName();
      return packageName.length() > 0 ? packageName + "." + getName() : getName();
    }

    return null;
  }

  public GrWildcardTypeArgument[] getTypeParametersGroovy() {
    return findChildrenByClass(GrWildcardTypeArgument.class);
  }

  public GrTypeDefinitionBody getBody() {
    return this.findChildByClass(GrTypeDefinitionBody.class);
  }

  @NotNull
  public GrMembersDeclaration[] getMemberDeclarations() {
    GrTypeDefinitionBody body = getBody();
    if (body == null) return GrMembersDeclaration.EMPTY_ARRAY;
    return body.getMemberDeclarations();
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation() {

      public String getPresentableText() {
        return getName();
      }

      @Nullable
      public String getLocationString() {
        PsiFile file = getContainingFile();
        if (file instanceof GroovyFile) {
          GroovyFile groovyFile = (GroovyFile) file;

          return groovyFile.getPackageName().length() > 0 ?
              "(" + groovyFile.getPackageName() + ")" :
              "";
        }
        return "";
      }

      @Nullable
      public Icon getIcon(boolean open) {
        return GroovyIcons.SMALLEST;
      }

      @Nullable
      public TextAttributesKey getTextAttributesKey() {
        return null;
      }
    };
  }

  @Nullable
  public GrExtendsClause getExtendsClause() {
    return findChildByClass(GrExtendsClause.class);
  }

  @Nullable
  public GrImplementsClause getImplementsClause() {
    return findChildByClass(GrImplementsClause.class);
  }

  public final String[] getSuperClassNames() {
    return ArrayUtil.mergeArrays(getExtendsNames(), getImplementsNames(), String.class);
  }

  protected String[] getImplementsNames() {
    GrImplementsClause implementsClause = getImplementsClause();
    GrCodeReferenceElement[] implementsRefs = implementsClause != null ? implementsClause.getReferenceElements() : GrCodeReferenceElement.EMPTY_ARRAY;
    ArrayList<String> implementsNames = new ArrayList<String>(implementsRefs.length);
    for (GrCodeReferenceElement ref : implementsRefs) {
      String name = ref.getReferenceName();
      if (name != null) implementsNames.add(name);
    }

    return implementsNames.toArray(new String[implementsNames.size()]);
  }

  protected String[] getExtendsNames() {
    GrExtendsClause extendsClause = getExtendsClause();
    GrCodeReferenceElement[] extendsRefs = extendsClause != null ? extendsClause.getReferenceElements() : GrCodeReferenceElement.EMPTY_ARRAY;
    ArrayList<String> extendsNames = new ArrayList<String>(extendsRefs.length);
    for (GrCodeReferenceElement ref : extendsRefs) {
      String name = ref.getReferenceName();
      if (name != null) extendsNames.add(name);
    }
    return extendsNames.toArray(new String[extendsNames.size()]);
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
      GroovyFileImpl file = (GroovyFileImpl) parent;
      if (file.getTypeDefinitions().length == 1 && !file.isScript()) {
        file.delete();
      } else {
        ASTNode astNode = file.getNode();
        if (astNode != null) {
          astNode.removeChild(getNode());
        }
      }
      return;
    }

    throw new IncorrectOperationException("Invalid type definition");
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull PsiSubstitutor substitutor, PsiElement lastParent, @NotNull PsiElement place) {
    for (final GrTypeParameter typeParameter : getTypeParameters()) {
      if (!ResolveUtil.processElement(processor, typeParameter)) return false;
    }

    NameHint nameHint = processor.getHint(NameHint.class);
    String name = nameHint == null ? null : nameHint.getName();
    ClassHint classHint = processor.getHint(ClassHint.class);
    if (classHint == null || classHint.shouldProcess(ClassHint.ResolveKind.PROPERTY)) {
      Map<String, CandidateInfo> fieldsMap = CollectClassMembersUtil.getAllFields(this);
      if (name != null) {
        CandidateInfo fieldInfo = fieldsMap.get(name);
        if (fieldInfo != null) {
          final PsiElement element = fieldInfo.getElement();
          if (!isSameDeclaration(place, element)) { //the same variable declaration
            if (!processor.execute(element, fieldInfo.getSubstitutor())) return false;
          }
        }
      } else {
        for (CandidateInfo info : fieldsMap.values()) {
          final PsiElement element = info.getElement();
          if (!isSameDeclaration(place, element)) {  //the same variable declaration
            if (!processor.execute(element, info.getSubstitutor())) return false;
          }
        }
      }
    }

    if (classHint == null || classHint.shouldProcess(ClassHint.ResolveKind.METHOD)) {
      Map<String, List<CandidateInfo>> methodsMap = CollectClassMembersUtil.getAllMethods(this, true);
      boolean isPlaceGroovy = place.getLanguage() == GroovyFileType.GROOVY_FILE_TYPE.getLanguage();
      if (name == null) {
        for (List<CandidateInfo> list : methodsMap.values()) {
          for (CandidateInfo info : list) {
            PsiMethod method = (PsiMethod) info.getElement();
            if (!isSameDeclaration(place, method) &&
                isMethodVisible(isPlaceGroovy, method) && !processor.execute(method, substitutor))
              return false;
          }
        }
      } else {
        List<CandidateInfo> byName = methodsMap.get(name);
        if (byName != null) {
          for (CandidateInfo info : byName) {
            PsiMethod method = (PsiMethod) info.getElement();
            if (!isSameDeclaration(place, method) &&
                isMethodVisible(isPlaceGroovy, method) && !processor.execute(method, substitutor))
              return false;
          }
        }

        final boolean isGetter = name.startsWith("get");
        final boolean isSetter = name.startsWith("set");
        if (isGetter || isSetter) {
          final String propName = StringUtil.decapitalize(name.substring(3));
          if (propName.length() > 0) {
            Map<String, CandidateInfo> fieldsMap = CollectClassMembersUtil.getAllFields(this); //cached
            final CandidateInfo info = fieldsMap.get(propName);
            if (info != null) {
              final PsiElement field = info.getElement();
              if (field instanceof GrField && ((GrField) field).isProperty() && isPropertyReference(place, (PsiField) field, isGetter)) {
                if (!processor.execute(field, info.getSubstitutor())) return false;
              }
            }
          }
        }
      }
    }

    return true;
  }

  private boolean isSameDeclaration(PsiElement place, PsiElement element) {
    if (element instanceof GrAccessorMethod) element = ((GrAccessorMethod) element).getProperty();

    return element instanceof GrField && PsiTreeUtil.isAncestor(element.getParent(), place, true);
  }

  private boolean isPropertyReference(PsiElement place, PsiField aField, boolean isGetter) {
    //filter only in groovy, todo: analyze java place
    if (place.getLanguage() != GroovyFileType.GROOVY_FILE_TYPE.getLanguage()) return true;

    if (place instanceof GrReferenceExpression) {
      final PsiElement parent = place.getParent();
      if (parent instanceof GrMethodCallExpression) {
        final GrMethodCallExpression call = (GrMethodCallExpression) parent;
        if (call.getNamedArguments().length > 0 || call.getClosureArguments().length > 0) return false;
        final GrExpression[] args = call.getExpressionArguments();
        if (isGetter) {
          return args.length == 0;
        } else {
          return args.length == 1 &&
              TypesUtil.isAssignableByMethodCallConversion(aField.getType(), args[0].getType(),
                  place.getManager(), place.getResolveScope());
        }
      }
    }

    return false;
  }

  private boolean isMethodVisible(boolean isPlaceGroovy, PsiMethod method) {
    return isPlaceGroovy || !(method instanceof DefaultGroovyMethod);
  }

  @NotNull
  public String getName() {
    return PsiImplUtil.getName(this);
  }

  //Fake java class implementation
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
    GrExtendsClause extendsClause = getExtendsClause();

    if (extendsClause != null) {
      GrCodeReferenceElement[] extendsRefElements = extendsClause.getReferenceElements();


      if (extendsRefElements.length > 0) {
        PsiClassType[] result = new PsiClassType[extendsRefElements.length];
        for (int j = 0; j < extendsRefElements.length; j++) {
          result[j] = new GrClassReferenceType(extendsRefElements[j]);
        }

        return result;
      }
    }

    if (!isInterface()) {
      return new PsiClassType[]{
          getManager().getElementFactory().createTypeByFQClassName("groovy.lang.GroovyObjectSupport", getResolveScope())
      };
    }

    return PsiClassType.EMPTY_ARRAY;
  }

  @NotNull
  public PsiClassType[] getImplementsListTypes() {
    GrImplementsClause implementsClause = getImplementsClause();

    PsiClassType[] result = PsiClassType.EMPTY_ARRAY;
    if (implementsClause != null) {
      GrCodeReferenceElement[] implementsRefElements = implementsClause.getReferenceElements();

      result = new PsiClassType[implementsRefElements.length];

      for (int j = 0; j < implementsRefElements.length; j++) {
        result[j] = new GrClassReferenceType(implementsRefElements[j]);
      }
    }

    return result;
  }

  @Nullable
  public final PsiClass getSuperClass() {
    final PsiClassType[] superTypes = getSuperTypes();
    if (superTypes.length > 0) {
      return superTypes[0].resolve();
    }
    return null;
  }

  public PsiClass[] getInterfaces() {
    GrImplementsClause implementsClause = findChildByClass(GrImplementsClause.class);
    if (implementsClause != null) {
      final GrCodeReferenceElement[] refs = implementsClause.getReferenceElements();
      List<PsiClass> result = new ArrayList<PsiClass>(refs.length);
      for (GrCodeReferenceElement ref : refs) {
        final PsiElement resolved = ref.resolve();
        if (resolved instanceof PsiClass) result.add((PsiClass) resolved);
      }

      return result.toArray(new PsiClass[result.size()]);
    }
    return PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  public final PsiClass[] getSupers() {
    PsiClassType[] superTypes = getSuperTypes();
    List<PsiClass> result = new ArrayList<PsiClass>();
    for (PsiClassType superType : superTypes) {
      PsiClass superClass = superType.resolve();
      if (superClass != null) {
        result.add(superClass);
      }
    }

    return result.toArray(new PsiClass[result.size()]);
  }

  @NotNull
  public final PsiClassType[] getSuperTypes() {
    return ArrayUtil.mergeArrays(getExtendsListTypes(), getImplementsListTypes(), PsiClassType.class);
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
    if (myMethods == null) {
      GrTypeDefinitionBody body = getBody();
      if (body != null) {
        myMethods = body.getMethods();
      } else {
        myMethods = GrMethod.EMPTY_ARRAY;
      }
    }
    return myMethods;
  }

  @NotNull
  public GrMethod[] getGroovyMethods() {
    if (myGroovyMethods == null) {
      GrTypeDefinitionBody body = getBody();
      if (body != null) {
        myGroovyMethods = body.getGroovyMethods();
      } else {
        myGroovyMethods = GrMethod.EMPTY_ARRAY;
      }
    }
    return myGroovyMethods;
  }

  public void subtreeChanged() {
    myMethods = null;
    myConstructors = null;
    myGroovyMethods = null;
    super.subtreeChanged();
  }

  @NotNull
  public PsiMethod[] getConstructors() {
    if (myConstructors == null) {
      List<GrMethod> result = new ArrayList<GrMethod>();
      for (final PsiMethod method : getMethods()) {
        if (method.isConstructor()) {
          result.add((GrMethod) method);
        }
      }

      myConstructors = result.toArray(new GrMethod[result.size()]);
      return myConstructors;
    }
    return myConstructors;
  }

  @NotNull
  public PsiClass[] getInnerClasses() {
    return PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  public PsiClassInitializer[] getInitializers() {
    return PsiClassInitializer.EMPTY_ARRAY;
  }

  @NotNull
  public PsiField[] getAllFields() {
    return PsiField.EMPTY_ARRAY;
  }

  @NotNull
  public PsiMethod[] getAllMethods() {
    List<PsiMethod> allMethods = new ArrayList<PsiMethod>();
    getAllMethodsInner(this, allMethods, new HashSet<PsiClass>());

    return allMethods.toArray(new PsiMethod[allMethods.size()]);
  }

  private static void getAllMethodsInner(PsiClass clazz, List<PsiMethod> allMethods, HashSet<PsiClass> visited) {
    if (visited.contains(clazz)) return;
    visited.add(clazz);

    allMethods.addAll(Arrays.asList(clazz.getMethods()));

    final PsiField[] fields = clazz.getFields();
    for (PsiField field : fields) {
      if (field instanceof GrField) {
        final GrField groovyField = (GrField) field;
        if (groovyField.isProperty()) {
          PsiMethod getter = groovyField.getGetter();
          if (getter != null) allMethods.add(getter);
          PsiMethod setter = groovyField.getSetter();
          if (setter != null) allMethods.add(setter);
        }
      }
    }

    final PsiClass[] supers = clazz.getSupers();
    for (PsiClass aSuper : supers) {
      getAllMethodsInner(aSuper, allMethods, visited);
    }
  }

  @NotNull
  public PsiClass[] getAllInnerClasses() {
    return PsiClass.EMPTY_ARRAY;
  }

  @Nullable
  public PsiField findFieldByName(String name, boolean checkBases) {
    if (!checkBases) {
      for (GrField field : getFields()) {
        if (name.equals(field.getName())) return field;
      }

      return null;
    }

    Map<String, CandidateInfo> fieldsMap = CollectClassMembersUtil.getAllFields(this);
    final CandidateInfo info = fieldsMap.get(name);
    return info == null ? null : (PsiField) info.getElement();
  }

  @Nullable
  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    final MethodSignature patternSignature = patternMethod.getSignature(PsiSubstitutor.EMPTY);
    for (PsiMethod method : findMethodsByName(patternMethod.getName(), checkBases, false)) {
      final PsiClass clazz = method.getContainingClass();
      PsiSubstitutor superSubstitutor = TypeConversionUtil.getClassSubstitutor(clazz, this, PsiSubstitutor.EMPTY);
      assert superSubstitutor != null;
      final MethodSignature signature = method.getSignature(superSubstitutor);
      if (signature.equals(patternSignature)) return method;
    }

    return null;
  }

  @NotNull
  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return findMethodsBySignature(patternMethod, checkBases, true);
  }

  @NotNull
  public PsiMethod[] findCodeMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return findMethodsBySignature(patternMethod, checkBases, false);
  }

  private PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases, boolean includeSynthetic) {
    ArrayList<PsiMethod> result = new ArrayList<PsiMethod>();
    final MethodSignature patternSignature = patternMethod.getSignature(PsiSubstitutor.EMPTY);
    for (PsiMethod method : findMethodsByName(patternMethod.getName(), checkBases, includeSynthetic)) {
      final PsiClass clazz = method.getContainingClass();
      PsiSubstitutor superSubstitutor = TypeConversionUtil.getClassSubstitutor(clazz, this, PsiSubstitutor.EMPTY);
      assert superSubstitutor != null;
      final MethodSignature signature = method.getSignature(superSubstitutor);
      if (signature.equals(patternSignature)) //noinspection unchecked
        result.add(method);
    }
    return result.toArray(new PsiMethod[result.size()]);
  }

  @NotNull
  public PsiMethod[] findMethodsByName(@NonNls String name, boolean checkBases) {
    return findMethodsByName(name, checkBases, true);
  }

  @NotNull
  public PsiMethod[] findCodeMethodsByName(@NonNls String name, boolean checkBases) {
    return findMethodsByName(name, checkBases, false);
  }

  private PsiMethod[] findMethodsByName(String name, boolean checkBases, boolean includeSyntheticAccessors) {
    if (!checkBases) {
      List<PsiMethod> result = new ArrayList<PsiMethod>();
      for (PsiMethod method : includeSyntheticAccessors ? getMethods() : getGroovyMethods()) {
        if (name.equals(method.getName())) result.add(method);
      }

      return result.toArray(new PsiMethod[result.size()]);
    }

    Map<String, List<CandidateInfo>> methodsMap = CollectClassMembersUtil.getAllMethods(this, includeSyntheticAccessors);
    return PsiImplUtil.mapToMethods(methodsMap.get(name));
  }

  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(String name, boolean checkBases) {
    final ArrayList<Pair<PsiMethod, PsiSubstitutor>> result = new ArrayList<Pair<PsiMethod, PsiSubstitutor>>();

    if (!checkBases) {
      final PsiMethod[] methods = findMethodsByName(name, false);
      for (PsiMethod method : methods) {
        result.add(new Pair<PsiMethod, PsiSubstitutor>(method, PsiSubstitutor.EMPTY));
      }
    } else {
      final Map<String, List<CandidateInfo>> map = CollectClassMembersUtil.getAllMethods(this, true);
      final List<CandidateInfo> candidateInfos = map.get(name);
      if (candidateInfos != null) {
        for (CandidateInfo info : candidateInfos) {
          final PsiElement element = info.getElement();
          result.add(new Pair<PsiMethod, PsiSubstitutor>((PsiMethod) element, info.getSubstitutor()));
        }
      }
    }

    return result;
  }

  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    final Map<String, List<CandidateInfo>> allMethodsMap = CollectClassMembersUtil.getAllMethods(this, true);
    List<Pair<PsiMethod, PsiSubstitutor>> result = new ArrayList<Pair<PsiMethod, PsiSubstitutor>>();
    for (List<CandidateInfo> infos : allMethodsMap.values()) {
      for (CandidateInfo info : infos) {
        result.add(new Pair<PsiMethod, PsiSubstitutor>((PsiMethod) info.getElement(), info.getSubstitutor()));
      }
    }

    return result;
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

  @Nullable
  public PsiIdentifier getNameIdentifier() {
    return new JavaIdentifier(getManager(), getContainingFile(), getNameIdentifierGroovy().getTextRange());
  }

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
  public PomMemberOwner getPom() {
    return null;
  }

  @Nullable
  public PsiClass getContainingClass() {
    return null;
  }

  @NotNull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return Collections.emptyList();
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    boolean renameFile = isRenameFileOnClassRenaming();

    PsiImplUtil.setName(name, getNameIdentifierGroovy());

    if (renameFile) {
      final GroovyFileBase file = (GroovyFileBase) getContainingFile();
      file.setName(name + "." + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension());
    }

    return this;
  }

  @Nullable
  public PsiModifierList getModifierList() {
    return findChildByClass(GrModifierList.class);
  }

  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    PsiModifierList modifierList = getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(name);
  }

  @Nullable
  public PsiDocComment getDocComment() {
    return null;
  }

  public boolean isDeprecated() {
    return false;
  }

  @Nullable
  public PsiMetaData getMetaData() {
    return null;
  }

  public boolean isMetaEnough() {
    return false;
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
    RowIcon rowIcon = ElementBase.createLayeredIcon(icon, ElementBase.getFlags(this, isLocked));
    IconUtil.setVisibilityIcon(getModifierList(), rowIcon);
    return rowIcon;
  }

  private Icon getIconInner() {
    if (isAnnotationType())
      return GroovyIcons.ANNOTAION;

    if (isInterface())
      return GroovyIcons.INTERFACE;

    if (isEnum())
      return GroovyIcons.ENUM;

    if (hasModifierProperty(PsiModifier.ABSTRACT))
      return GroovyIcons.ABSTRACT;

    return GroovyIcons.CLAZZ;
  }

  private boolean isRenameFileOnClassRenaming() {
    final PsiFile file = getContainingFile();
    if (!(file instanceof GroovyFile)) return false;
    final GroovyFile groovyFile = (GroovyFile) file;
    if (groovyFile.isScript()) return false;
    final GrTypeDefinition[] typeDefinitions = groovyFile.getTypeDefinitions();
    if (typeDefinitions.length > 1) return false;
    final String name = getName();
    final VirtualFile vFile = groovyFile.getVirtualFile();
    return vFile != null && name.equals(vFile.getNameWithoutExtension());
  }

  public String getElementToCompare() {
    return getName();
  }

  public PsiElement getOriginalElement() {
    return PsiImplUtil.getOriginalElement(this, getContainingFile());
  }

  public <T extends GrMembersDeclaration> T addMemberDeclaration(@NotNull T decl, PsiElement anchorBefore) throws IncorrectOperationException {

    GrTypeDefinitionBody body = getBody();
    if (body == null) throw new IncorrectOperationException("Type definition without a body");
    ASTNode anchorNode;
    if (anchorBefore != null) {
      anchorNode = anchorBefore.getNode();
    } else {
      PsiElement child = body.getLastChild();
      assert child != null;
      anchorNode = child.getNode();
    }
    ASTNode bodyNode = body.getNode();
    bodyNode.addChild(decl.getNode(), anchorNode);
    bodyNode.addLeaf(GroovyTokenTypes.mWS, " ", decl.getNode()); //add whitespaces before and after to hack over incorrect auto reformat
    bodyNode.addLeaf(GroovyTokenTypes.mWS, " ", anchorNode);
    return decl;
  }

  public void removeMemberDeclaration(GrMembersDeclaration decl) {
    GrTypeDefinitionBody body = getBody();
    if (body == null) throw new PsiInvalidElementAccessException(decl);

    body.getNode().removeChild(decl.getNode());
  }
}
