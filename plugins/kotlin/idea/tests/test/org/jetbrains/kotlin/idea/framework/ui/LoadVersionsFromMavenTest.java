// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.framework.ui;

import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.util.text.VersionComparatorUtil;
import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.runner.RunWith;

import java.util.Collection;

@RunWith(JUnit38ClassRunner.class)
public class LoadVersionsFromMavenTest extends LightIdeaTestCase {
    public void testDownload() throws Exception {
        Collection<String> versions = ConfigureDialogWithModulesAndVersion.loadVersions("1.0.0");
        assertTrue(versions.size() > 0);
        for (String version : versions) {
            assertTrue(VersionComparatorUtil.compare(version, "1.0.0") >= 0);
        }
    }
}
