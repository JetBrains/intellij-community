// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.framework.ui;

import com.intellij.platform.testFramework.io.ExternalResourcesChecker;
import com.intellij.testFramework.JUnit38AssumeSupportRunner;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.util.text.VersionComparatorUtil;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;

@RunWith(JUnit38AssumeSupportRunner.class)
public class LoadVersionsFromMavenTest extends LightIdeaTestCase {
    public void testDownload() {
        Collection<String> versions;
        try {
            versions = ConfigureDialogWithModulesAndVersion.loadVersions("1.0.0");
        } catch (IOException e) {
            ExternalResourcesChecker.reportUnavailability("Kotlin artifact repository", e);
            return;
        }

        assertTrue(versions.size() > 0);
        for (String version : versions) {
            assertTrue(VersionComparatorUtil.compare(version, "1.0.0") >= 0);
            assertFalse(version.contains("-"));
            assertFalse(version.toLowerCase(Locale.ROOT).contains("beta"));
            assertFalse(version.toLowerCase(Locale.ROOT).contains("rc"));
        }
    }
}
