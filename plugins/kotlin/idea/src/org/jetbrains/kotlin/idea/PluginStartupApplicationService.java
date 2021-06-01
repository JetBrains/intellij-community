// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.intellij.util.io.PathKt.exists;
import static com.intellij.util.io.PathKt.isFile;
import static java.nio.file.Files.deleteIfExists;

public class PluginStartupApplicationService implements Disposable {

    public static PluginStartupApplicationService getInstance() {
        return ApplicationManager.getApplication().getService(PluginStartupApplicationService.class);
    }

    private String aliveFlagPath;

    public synchronized String getAliveFlagPath() {
        if (this.aliveFlagPath == null) {
            try {
                Path flagFile = Files.createTempFile("kotlin-idea-", "-is-running");
                File file = flagFile.toFile();
                Disposer.register(this, new Disposable() {
                    @Override
                    public void dispose() {
                        file.delete();
                    }
                });
                this.aliveFlagPath = flagFile.toAbsolutePath().toString();
            } catch (IOException e) {
                this.aliveFlagPath = "";
            }
        }
        return this.aliveFlagPath;
    }

    public synchronized void resetAliveFlag() {
        if (aliveFlagPath != null && !aliveFlagPath.isEmpty()) {
            Path flagFile = Path.of(aliveFlagPath);
            try {
                if (isFile(flagFile) && exists(flagFile) && deleteIfExists(flagFile)) {
                    this.aliveFlagPath = null;
                }
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void dispose() {

    }
}
