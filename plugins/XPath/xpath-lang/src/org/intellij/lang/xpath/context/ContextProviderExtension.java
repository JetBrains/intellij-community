package org.intellij.lang.xpath.context;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.intellij.lang.xpath.XPathFile;

public abstract class ContextProviderExtension {
    public static final ExtensionPointName<ContextProviderExtension> EXTENSION_POINT_NAME =
            ExtensionPointName.create("XPathView.xpath.contextProviderExtension");

    protected abstract boolean accepts(XPathFile file);

    @NotNull
    protected abstract ContextProvider getContextProvider(XPathFile file);

    @Nullable
    public static ContextProvider getInstance(XPathFile file) {
        final ContextProviderExtension[] extensions = Extensions.getExtensions(EXTENSION_POINT_NAME);
        for (ContextProviderExtension extension : extensions) {
            if (extension.accepts(file)) {
                return extension.getContextProvider(file);
            }
        }
        return null;
    }
}