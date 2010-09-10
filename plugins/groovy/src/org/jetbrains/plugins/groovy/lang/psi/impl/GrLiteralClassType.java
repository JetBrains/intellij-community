package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

/**
 * @author peter
 */
public abstract class GrLiteralClassType extends PsiClassType {
  protected final GlobalSearchScope myScope;
  protected final JavaPsiFacade myFacade;

  public GrLiteralClassType(LanguageLevel languageLevel, GlobalSearchScope scope, JavaPsiFacade facade) {
    super(languageLevel);
    myScope = scope;
    myFacade = facade;
  }

  protected abstract String getJavaClassName();

  @NotNull
  public ClassResolveResult resolveGenerics() {
    return new ClassResolveResult() {
      private final PsiClass myBaseClass = resolve();

      public PsiClass getElement() {
        return myBaseClass;
      }

      public PsiSubstitutor getSubstitutor() {
        PsiSubstitutor result = PsiSubstitutor.EMPTY;
        if (myBaseClass != null) {
          final PsiType[] typeArgs = getParameters();
          final PsiTypeParameter[] typeParams = myBaseClass.getTypeParameters();
          if (typeParams.length == typeArgs.length) {
            for (int i = 0; i < typeArgs.length; i++) {
              result = result.put(typeParams[i], typeArgs[i]);
            }
          }
        }
        return result;
      }

      public boolean isPackagePrefixPackageReference() {
        return false;
      }

      public boolean isAccessible() {
        return true;
      }

      public boolean isStaticsScopeCorrect() {
        return true;
      }

      public PsiElement getCurrentFileResolveScope() {
        return null;
      }

      public boolean isValidResult() {
        return isStaticsScopeCorrect() && isAccessible();
      }
    };
  }

  @Nullable
  public String getPresentableText() {
    String name = getClassName();
    if (name == null) return null;
    final PsiType[] params = getParameters();
    if (params.length == 0 || params[0] == null) return name;

    return name + "<" + StringUtil.join(params, new Function<PsiType, String>() {
      @Override
      public String fun(PsiType psiType) {
        return psiType.getPresentableText();
      }
    }, ", ") + ">";
  }

  @Nullable
  public String getCanonicalText() {
    PsiClass resolved = resolve();
    if (resolved == null) return null;
    final String name = resolved.getQualifiedName();
    if (name==null) return null;

    final PsiType[] params = getParameters();
    if (params.length==0 || params[0]==null) return name;

    return name + "<" + StringUtil.join(params, new Function<PsiType, String>() {
      @Override
      public String fun(PsiType psiType) {
        return psiType.getCanonicalText();
      }
    }, ", ") + ">";
  }

  @NotNull
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  public GlobalSearchScope getScope() {
    return myScope;
  }

  @Nullable
  public PsiClass resolve() {
    return myFacade.findClass(getJavaClassName(), getResolveScope());
  }

  @NotNull
  public PsiClassType rawType() {
    return myFacade.getElementFactory().createTypeByFQClassName(getJavaClassName(), myScope);
  }

  public boolean equalsToText(@NonNls String text) {
    return text.equals(getJavaClassName());
  }

  @NotNull
  public GlobalSearchScope getResolveScope() {
    return myScope;
  }

  protected static String getInternalCanonicalText(@Nullable PsiType type) {
    return type == null ? CommonClassNames.JAVA_LANG_OBJECT : type.getInternalCanonicalText();
  }

  @NotNull
  protected PsiType getLeastUpperBound(PsiType[] psiTypes) {
    PsiType result = null;
    final PsiManager manager = getPsiManager();
    for (final PsiType other : psiTypes) {
      result = TypesUtil.getLeastUpperBoundNullable(result, other, manager);
    }
    return result == null ? PsiType.getJavaLangObject(manager, getResolveScope()) : result;
  }

  protected PsiManager getPsiManager() {
    return PsiManager.getInstance(myFacade.getProject());
  }
}
