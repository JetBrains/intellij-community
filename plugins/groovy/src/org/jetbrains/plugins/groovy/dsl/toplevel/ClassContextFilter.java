package org.jetbrains.plugins.groovy.dsl.toplevel;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.Map;

/**
 * @author peter
 */
public class ClassContextFilter implements ContextFilter {
  private final Condition<Pair<PsiType, PsiFile>> myPattern;

  public ClassContextFilter(Condition<Pair<PsiType, PsiFile>> pattern) {
    myPattern = pattern;
  }

  public boolean isApplicable(GroovyClassDescriptor descriptor, ProcessingContext ctx) {
    final PsiFile place = descriptor.getPlaceFile();
    return myPattern.value(Pair.create(findPsiType(descriptor, ctx), place));
  }

  @NotNull
  public static PsiType findPsiType(GroovyClassDescriptor descriptor, ProcessingContext ctx) {
    String typeText = descriptor.getTypeText();
    final String key = getClassKey(typeText);
    final Object cached = ctx.get(key);
    if (cached instanceof PsiType) {
      return (PsiType)cached;
    }

    final PsiType found = JavaPsiFacade.getElementFactory(descriptor.getProject()).createTypeFromText(typeText, descriptor.getPlaceFile());
    ctx.put(key, found);
    return found;
  }

  public static String getClassKey(String fqName) {
    return "Class: " + fqName;
  }

  public static ClassContextFilter fromClassPattern(final ElementPattern pattern) {
    return new ClassContextFilter(new Condition<Pair<PsiType, PsiFile>>() {
      @Override
      public boolean value(Pair<PsiType, PsiFile> pair) {
        final PsiType type = pair.first;
        return type instanceof PsiClassType ? pattern.accepts(((PsiClassType)type).resolve()) : false;
      }
    });
  }

  public static ClassContextFilter subtypeOf(final String typeText) {
    return new ClassContextFilter(new Condition<Pair<PsiType, PsiFile>>() {
      @Override
      public boolean value(Pair<PsiType, PsiFile> p) {
        PsiFile place = p.second;
        //PsiType myType = JavaPsiFacade.getElementFactory(place.getProject()).createTypeFromText(typeText, place);
        PsiType myType = getCachedType(typeText, place);
        if (p.first == PsiType.NULL) return myType == PsiType.NULL;
        return TypesUtil.isAssignable(myType, p.first, place.getManager(), place.getResolveScope(), false);
      }
    });
  }

  private static final Key<Map<String, PsiType>> CACHED_TYPES = Key.create("Cached types");

  private static PsiType getCachedType(String typeText, PsiFile context) {
    Map<String, PsiType> map = context.getUserData(CACHED_TYPES);
    if (map == null) {
      map = new ConcurrentHashMap<String, PsiType>();
      context.putUserData(CACHED_TYPES, map);
    }
    PsiType type = map.get(typeText);
    if (type == null || !type.isValid()) {
      type = JavaPsiFacade.getElementFactory(context.getProject()).createTypeFromText(typeText, context);
      map.put(typeText, type);
    }
    return type;
  }
}
