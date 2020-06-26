// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.netbeans.lib.cvsclient.command.update;

import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.admin.Entry;

import java.io.File;

public class UpdatedFileInfo {

    public static final class UpdatedType{
        public static final UpdatedType UPDATED = new UpdatedType();
        public static final UpdatedType MERGED = new UpdatedType();
        public static final UpdatedType REMOVED = new UpdatedType();
        private UpdatedType(){

        }
    }

    private final FileObject fileObject;
    private final File file;
    private final UpdatedType type;
    private final Entry entry;


    public UpdatedFileInfo(FileObject fileObject, File file, UpdatedType merged, Entry entry) {
        this.fileObject = fileObject;
        this.file = file;
        this.type = merged;
        this.entry = entry;
    }

    public FileObject getFileObject() {
        return fileObject;
    }

    public File getFile() {
        return file;
    }

    public UpdatedType getType() {
        return type;
    }

    public Entry getEntry() {
        return entry;
    }
}
