package org.jetbrains.android.dom.converters;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.android.dom.LookupClass;
import org.jetbrains.android.dom.LookupPrefix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class ConstantFieldConverter extends ResolvingConverter<String> {
    @NotNull
    public Collection<? extends String> getVariants(ConvertContext context) {
        List<String> result = new ArrayList<String>();
        DomElement element = context.getInvocationElement();
        LookupClass lookupClass = element.getAnnotation(LookupClass.class);
        LookupPrefix lookupPrefix = element.getAnnotation(LookupPrefix.class);
        if (lookupClass != null && lookupPrefix != null) {
            PsiClass psiClass = JavaPsiFacade.getInstance(context.getPsiManager().getProject()).findClass(lookupClass.value(),
                    GlobalSearchScope.allScope(context.getModule().getProject()));
            if (psiClass != null) {
                PsiField[] psiFields = psiClass.getFields();
                for(PsiField field: psiFields) {
                    if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.PUBLIC)) {
                        PsiExpression initializer = field.getInitializer();
                        if (initializer instanceof PsiLiteralExpression) {
                            PsiLiteralExpression literalExpression = (PsiLiteralExpression) initializer;
                            Object o = literalExpression.getValue();
                            if (o instanceof String && o.toString().startsWith(lookupPrefix.value())) {
                                result.add(o.toString());
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    public String fromString(@Nullable @NonNls String s, ConvertContext context) {
        return s;
    }

    public String toString(@Nullable String  value, ConvertContext context) {
        return value;
    }
}
