package ru.adelf.idea.dotenv.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFileFactory;
import ru.adelf.idea.dotenv.DotEnvFileType;

public class DotEnvElementFactory {
    public static DotEnvProperty createProperty(Project project, String name) {
        final DotEnvFile file = createFile(project, name);
        return (DotEnvProperty) file.getFirstChild();
    }

    public static DotEnvFile createFile(Project project, String text) {
        String name = "dummy.env";
        return (DotEnvFile) PsiFileFactory.getInstance(project).
                createFileFromText(name, DotEnvFileType.INSTANCE, text);
    }
}
