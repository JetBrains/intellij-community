package org.jetbrains.android.fileTypes;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
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
        return IconLoader.getIcon("/icons/android.png");
    }

    @NotNull
    @NonNls
    public String getName() {
        return "AIDL";
    }
}
