/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.fileTypes;

import com.intellij.openapi.fileTypes.LanguageFileType;
import icons.AndroidIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * File type for *.aidl files.
 *
 * @author Alexey Efimov
 */
public class AndroidIdlFileType extends LanguageFileType {
    public static final AndroidIdlFileType ourFileType = new AndroidIdlFileType();

    @NonNls
    public static final String DEFAULT_ASSOCIATED_EXTENSION = "aidl";

    private AndroidIdlFileType() {
        super(new AndroidIdlLanguage());
    }

    @NotNull
    @NonNls
    public String getDefaultExtension() {
        return DEFAULT_ASSOCIATED_EXTENSION;
    }

    @NotNull
    public String getDescription() {
        return AndroidBundle.message("aidl.filetype.description");
    }

    @Nullable
    public Icon getIcon() {
        return AndroidIcons.Android;
    }

    @NotNull
    @NonNls
    public String getName() {
        return "AIDL";
    }
}
