package ru.adelf.idea.dotenv.php;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.adelf.idea.dotenv.DotEnvSettings;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesApi;
import ru.adelf.idea.dotenv.common.BaseEnvCompletionProvider;

import java.util.Arrays;
import java.util.List;

public class PhpunitEnvCompletionContributor extends BaseEnvCompletionProvider implements GotoDeclarationHandler {
    public static final List<String> TAGS = Arrays.asList("server", "env");

    public PhpunitEnvCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(XmlToken.class).withParent(XmlAttributeValue.class), new CompletionProvider<CompletionParameters>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters completionParameters, @NotNull ProcessingContext processingContext, @NotNull CompletionResultSet completionResultSet) {

                PsiElement psiElement = completionParameters.getOriginalPosition();

                if (psiElement == null || !DotEnvSettings.getInstance().completionEnabled) {
                    return;
                }

                if (getXmlAttributeValue(psiElement) == null) {
                    return;
                }

                fillCompletionResultSet(completionResultSet, psiElement.getProject());
            }
        });
    }

    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement psiElement, int i, Editor editor) {

        if (psiElement == null) {
            return PsiElement.EMPTY_ARRAY;
        }

        XmlAttributeValue stringLiteral = getXmlAttributeValue(psiElement);

        if (stringLiteral == null) {
            return PsiElement.EMPTY_ARRAY;
        }

        return EnvironmentVariablesApi.getKeyDeclarations(psiElement.getProject(), stringLiteral.getValue());
    }

    @Nullable
    private XmlAttributeValue getXmlAttributeValue(@NotNull PsiElement psiElement) {
        PsiElement attributeValue = psiElement.getParent();

        if (!(attributeValue instanceof XmlAttributeValue)) return null;

        PsiElement attribute = attributeValue.getParent();

        if (!(attribute instanceof XmlAttribute)) return null;

        if (!((XmlAttribute) attribute).getName().equals("name")) return null;

        if (!(attribute.getParent() instanceof XmlTag)) return null;

        XmlTag tag = (XmlTag) attribute.getParent();

        if (!TAGS.contains(tag.getName())) return null;

        if (tag.getParentTag() == null || !tag.getParentTag().getName().equals("php")) return null;

        return (XmlAttributeValue) attributeValue;
    }

    @Nullable
    @Override
    public String getActionText(@NotNull DataContext dataContext) {
        return null;
    }
}
