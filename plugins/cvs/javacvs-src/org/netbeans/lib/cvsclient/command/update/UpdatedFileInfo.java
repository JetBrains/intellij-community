/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package org.netbeans.lib.cvsclient.command.update;

import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.admin.Entry;

import java.io.File;

public class UpdatedFileInfo {

    public static class UpdatedType{
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
