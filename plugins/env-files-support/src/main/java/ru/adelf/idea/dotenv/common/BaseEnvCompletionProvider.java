package ru.adelf.idea.dotenv.common;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.project.Project;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesApi;

import java.util.Map;

abstract public class BaseEnvCompletionProvider extends CompletionContributor implements GotoDeclarationHandler {

    protected void fillCompletionResultSet(@NotNull CompletionResultSet completionResultSet, @NotNull Project project) {
        for (Map.Entry<String, String> entry : EnvironmentVariablesApi.getAllKeyValues(project).entrySet()) {
            LookupElementBuilder lockup = LookupElementBuilder.create(entry.getKey())
                    .withCaseSensitivity(false);

            if (StringUtils.isNotEmpty(entry.getValue())) {
                lockup = lockup.withTailText(" = " + entry.getValue(), true);
            }

            completionResultSet.addElement(PrioritizedLookupElement.withPriority(lockup, 100));
        }
    }
}
