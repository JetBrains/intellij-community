package com.resources;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.siyeh.ig.IGInspectionTestCase;
import com.siyeh.ig.resources.IOResourceInspection;

/**
 * @author Alexey
 */
public class IOResourceTest extends IGInspectionTestCase {

    @Override
    protected Sdk getTestProjectSdk() {
        LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
        return JavaSdkImpl.getMockJdk17();
    }

    public void test() throws Exception {
        doTest("com/siyeh/igtest/resources/io/plain", new IOResourceInspection());
    }

    public void testInsideTry() throws Exception {
        final IOResourceInspection inspection = new IOResourceInspection();
        inspection.insideTryAllowed = true;
        doTest("com/siyeh/igtest/resources/io/inside_try", inspection);
    }
}
