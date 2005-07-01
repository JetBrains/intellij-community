package org.intellij.images.editor.impl;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.jdom.Element;

/**
 * Image editor provider.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ImageFileEditorProvider implements ApplicationComponent, FileEditorProvider {
    private static final String NAME = "ImageEditorProvider";
    private static final String EDITOR_TYPE_ID = "images";

    private final ImageFileTypeManager typeManager;

    ImageFileEditorProvider(ImageFileTypeManager typeManager) {
        this.typeManager = typeManager;
    }

    public String getComponentName() {
        return NAME;
    }

    public void initComponent() {
    }

    public void disposeComponent() {
    }

    public boolean accept(Project project, VirtualFile file) {
        return typeManager.isImage(file);
    }

    public FileEditor createEditor(Project project, VirtualFile file) {
        return new ImageFileEditorImpl(project, file);
    }

    public void disposeEditor(FileEditor editor) {
        ImageFileEditorImpl fileEditor = (ImageFileEditorImpl)editor;
        fileEditor.dispose();
    }

    public FileEditorState readState(Element sourceElement, Project project, VirtualFile file) {
        return new FileEditorState() {
            public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
                return false;
            }
        };
    }

    public void writeState(FileEditorState state, Project project, Element targetElement) {
    }

    public String getEditorTypeId() {
        return EDITOR_TYPE_ID;
    }

    public FileEditorPolicy getPolicy() {
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
    }
}
