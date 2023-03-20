// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.compilerPreferences.configuration;

import com.intellij.openapi.util.NlsSafe;
import kotlin.KotlinVersion;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.base.compilerPreferences.KotlinBaseCompilerConfigurationUiBundle;
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion;
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings;
import org.jetbrains.kotlin.utils.DescriptionAware;

import java.util.Objects;

class JpsVersionItem implements DescriptionAware {
    private final @Nullable IdeKotlinVersion myVersion;
    private final @NlsSafe @Nullable String myText;

    final boolean myEnabled;

    private JpsVersionItem(@NlsSafe @Nullable IdeKotlinVersion version, boolean enabled, @Nullable String text) {
        myVersion = version;
        myEnabled = enabled;
        myText = text;
        assert myVersion != null || myText != null;
    }

    JpsVersionItem(@NotNull IdeKotlinVersion version) {
        this(version, true, null);
    }

    static JpsVersionItem createLabel(@NotNull @Nls String text) {
        return new JpsVersionItem(null, false, text);
    }

    static JpsVersionItem createFromRawVersion(@NotNull @Nls String rawVersion) {
        IdeKotlinVersion ideKotlinVersion = IdeKotlinVersion.opt(rawVersion);
        return new JpsVersionItem(ideKotlinVersion, true, ideKotlinVersion != null ? null : rawVersion);
    }

    @Override
    public @NotNull String getDescription() {
        if (myVersion == null) return Objects.requireNonNull(myText);
        if (myVersion.equals(KotlinJpsPluginSettings.getBundledVersion())) {
            return KotlinBaseCompilerConfigurationUiBundle.message("configuration.text.bundled.0.jps.version", myVersion);
        }

        KotlinVersion kotlinVersion = myVersion.getKotlinVersion();
        if (kotlinVersion.compareTo(KotlinJpsPluginSettings.getJpsMinimumSupportedVersion()) < 0 ||
            kotlinVersion.compareTo(KotlinJpsPluginSettings.getJpsMaximumSupportedVersion()) > 0) {
            return KotlinBaseCompilerConfigurationUiBundle.message("configuration.text.0.unsupported.jps.version", myVersion);
        }

        return myVersion.toString();
    }

    public @Nullable IdeKotlinVersion getVersion() {
        return myVersion;
    }

    public @NotNull String getRawVersion() {
        return myVersion != null ? myVersion.getRawVersion() : Objects.requireNonNull(myText);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JpsVersionItem item = (JpsVersionItem) o;
        return Objects.equals(myVersion, item.myVersion) && Objects.equals(myText, item.myText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(myVersion);
    }
}
