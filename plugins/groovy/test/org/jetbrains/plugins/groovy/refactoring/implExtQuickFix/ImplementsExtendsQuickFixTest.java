package org.jetbrains.plugins.groovy.refactoring.implExtQuickFix;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Test;
import org.jetbrains.plugins.groovy.annotator.intentions.ChangeExtendsImplementsQuickFix;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.testcases.simple.SimpleGroovyFileSetTestCase;
import org.jetbrains.plugins.groovy.util.PathUtil;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * User: Dmitry.Krasilschikov
 * Date: 11.10.2007
 */
public class ImplementsExtendsQuickFixTest extends SimpleGroovyFileSetTestCase {
    private static final String DATA_PATH = PathUtil.getDataPath(ImplementsExtendsQuickFixTest.class);

    public ImplementsExtendsQuickFixTest(String path) {
        super(path);
    }

    public String transform(String testName, String[] data) throws Exception {
        String fileText = data[0];
        final PsiFile psiFile = TestUtils.createPseudoPhysicalGroovyFile(myProject, fileText);
        assert psiFile instanceof GroovyFileBase;
        final GrTypeDefinition[] typeDefinitions = ((GroovyFileBase) psiFile).getTypeDefinitions();
        final GrTypeDefinition typeDefinition = typeDefinitions[typeDefinitions.length - 1];
        if (typeDefinition.getImplementsClause() == null && typeDefinition.getExtendsClause() == null) {
            return "";
        }
        final String[] newFileText = new String[]{""};

        CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
            public void run() {
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    public void run() {
                        try {
                            ChangeExtendsImplementsQuickFix fix = new ChangeExtendsImplementsQuickFix(typeDefinition.getExtendsClause(), typeDefinition.getImplementsClause());
                            fix.invoke(myProject, null, psiFile);
                            final GrTypeDefinition[] newTypeDefinitions = ((GroovyFileBase) psiFile).getTypeDefinitions();
                            newFileText[0] = newTypeDefinitions[newTypeDefinitions.length - 1].getText();                                      
                        } catch (IncorrectOperationException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }, null, null);

        System.out.println(newFileText[0]);
        return newFileText[0];
    }

    public static Test suite() {
        return new ImplementsExtendsQuickFixTest(DATA_PATH);
    }
}
