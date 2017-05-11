package ru.adelf.idea.dotenv.api;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import javafx.util.Pair;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.indexing.DotEnvKeyValuesIndex;
import ru.adelf.idea.dotenv.indexing.DotEnvKeysIndex;
import ru.adelf.idea.dotenv.indexing.DotEnvUsagesIndex;
import ru.adelf.idea.dotenv.util.EnvironmentVariablesProviderUtil;
import ru.adelf.idea.dotenv.util.EnvironmentVariablesUtil;

import java.util.*;

public class EnvironmentVariablesApi {

    @NotNull
    public static Map<String, String> getAllKeyValues(Project project) {
        FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
        Map<String, String> keyValues = new HashMap<>();

        fileBasedIndex.processAllKeys(DotEnvKeyValuesIndex.KEY, s -> {
            if(fileBasedIndex.getContainingFiles(DotEnvKeyValuesIndex.KEY, s, GlobalSearchScope.allScope(project)).size() > 0) {

                Pair<String, String> keyValue = EnvironmentVariablesUtil.getKeyValueFromString(s);

                if(keyValues.containsKey(keyValue.getKey())) return true;

                keyValues.put(keyValue.getKey(), keyValue.getValue());
            }

            return true;
        }, project);

        return keyValues;
    }

    /**
     *
     * @param project project
     * @param key environment variable key
     * @return All key declarations, in .env files, Dockerfile, docker-compose.yml, etc
     */
    @NotNull
    public static PsiElement[] getKeyDeclarations(Project project, String key) {
        List<PsiElement> targets = new ArrayList<>();

        FileBasedIndex.getInstance().getFilesWithKey(DotEnvKeysIndex.KEY, new HashSet<>(Collections.singletonList(key)), virtualFile -> {
            PsiFile psiFileTarget = PsiManager.getInstance(project).findFile(virtualFile);
            if(psiFileTarget == null) {
                return true;
            }

            for(EnvironmentVariablesProvider provider : EnvironmentVariablesProviderUtil.PROVIDERS) {
                if(provider.acceptFile(virtualFile)) {
                    targets.addAll(EnvironmentVariablesUtil.getElementsByKey(key, provider.getElements(psiFileTarget)));
                }
            }

            return true;
        }, GlobalSearchScope.allScope(project));

        return targets.toArray(new PsiElement[0]);
    }

    /**
     *
     * @param project project
     * @param key environment variable key
     * @return All key usages, like getenv('KEY')
     */
    @NotNull
    public static PsiElement[] getKeyUsages(Project project, String key) {
        List<PsiElement> targets = new ArrayList<>();

        FileBasedIndex.getInstance().getFilesWithKey(DotEnvUsagesIndex.KEY, new HashSet<>(Collections.singletonList(key)), virtualFile -> {
            PsiFile psiFileTarget = PsiManager.getInstance(project).findFile(virtualFile);
            if(psiFileTarget == null) {
                return true;
            }

            for(EnvironmentVariablesUsagesProvider provider : EnvironmentVariablesProviderUtil.USAGES_PROVIDERS) {
                if(provider.acceptFile(virtualFile)) {
                    targets.addAll(EnvironmentVariablesUtil.getUsagesElementsByKey(key, provider.getUsages(psiFileTarget)));
                }
            }

            return true;
        }, GlobalSearchScope.allScope(project));

        return targets.toArray(new PsiElement[0]);
    }
}
