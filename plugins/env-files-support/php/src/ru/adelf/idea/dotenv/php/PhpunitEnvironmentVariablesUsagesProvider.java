package ru.adelf.idea.dotenv.php;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesUsagesProvider;
import ru.adelf.idea.dotenv.models.KeyUsagePsiElement;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

public class PhpunitEnvironmentVariablesUsagesProvider implements EnvironmentVariablesUsagesProvider {
    @Override
    public boolean acceptFile(VirtualFile file) {
        return file.getFileType().equals(XmlFileType.INSTANCE) && file.getName().equals("phpunit.xml");
    }

    @NotNull
    @Override
    public Collection<KeyUsagePsiElement> getUsages(PsiFile psiFile) {
        if (!(psiFile instanceof XmlFile)) return Collections.emptyList();

        if (!(psiFile.getFirstChild() instanceof XmlDocument)) return Collections.emptyList();

        XmlTag rootTag = ((XmlDocument) psiFile.getFirstChild()).getRootTag();

        if (rootTag == null) return Collections.emptyList();

        XmlTag phpTag = rootTag.findFirstSubTag("php");

        if (phpTag == null) return Collections.emptyList();

        Collection<KeyUsagePsiElement> collectedItems = new HashSet<>();

        for (XmlTag tag : phpTag.getSubTags()) {
            if (tag.getName().equals("server") || tag.getName().equals("env")) {
                XmlAttribute attribute = tag.getAttribute("name");

                if (attribute != null && attribute.getValueElement() != null) {
                    collectedItems.add(new KeyUsagePsiElement(attribute.getValue(), attribute.getValueElement()));
                }
            }
        }

        return collectedItems;
    }
}
