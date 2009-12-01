/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.maddyhome.idea.copyright.util;

import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.maddyhome.idea.copyright.CopyrightUpdaters;
import com.maddyhome.idea.copyright.options.LanguageOptions;

import java.util.*;

public class FileTypeUtil
{
    public static synchronized FileTypeUtil getInstance()
    {
        if (instance == null)
        {
            instance = new FileTypeUtil();
        }

        return instance;
    }

    public static String buildComment(FileType type, String template, LanguageOptions options)
    {

      Commenter commenter = getCommenter(type);
        if (commenter == null)
        {
            return "<No comments>";
        }

        String bs = commenter.getBlockCommentPrefix();
        String be = commenter.getBlockCommentSuffix();
        String ls = commenter.getLineCommentPrefix();

        if ((bs == null || be == null) && ls == null)
        {
            return "<No comments>";
        }

        boolean allowBlock = bs != null && be != null;
        boolean allowLine = ls != null;
        if (allowLine && !allowBlock)
        {
            bs = ls;
            be = ls;
        }

        boolean allowSeparator = getInstance().allowSeparators(type);
        char filler = options.getFiller();
        if (!allowSeparator)
        {
            if (options.getFiller() == LanguageOptions.DEFAULT_FILLER)
            {
                filler = '~';
            }
        }

        boolean isBlock = options.isBlock();
        boolean isPrefix = options.isPrefixLines();
        if (isBlock && !allowBlock)
        {
            isPrefix = true;
        }
        boolean isBox = options.isBox() && options.isSeparateBefore() && options.isSeparateAfter() &&
            options.getLenBefore() == options.getLenAfter();

        StringBuffer preview = new StringBuffer(80);
        String open = isBlock ? bs : allowLine ? ls : bs;
        String close = isBlock ? be : allowLine ? ls : be;
        StringBuffer pre = new StringBuffer(5);
        StringBuffer leader = new StringBuffer(5);
        StringBuffer post = new StringBuffer(5);
        if (filler == LanguageOptions.DEFAULT_FILLER)
        {
            filler = open.charAt(open.length() - 1);
        }
        int offset = 0;
        if (isBlock)
        {
            int pos = open.length() - 1;
            pre.append(allowBlock ? filler : open.charAt(pos));
            while (pos > 0 && open.charAt(pos) == open.charAt(open.length() - 1))
            {
                pos--;
                offset++;
            }
            while (open.length() > 1 && pos >= 0)
            {
                leader.append(' ');
                pos--;
            }
            post.append(filler);
            if (!isPrefix)
            {
                pre = new StringBuffer(0);
            }
            if (!allowBlock)
            {
                close = Character.toString(filler);
            }
        }
        else
        {
            if (allowLine)
            {
                close = Character.toString(filler);
            }
            pre.append(open);
            post.append(close);
        }

        int diff = 0;
        if (options.isSeparateBefore())
        {
            if (isBlock && isBox && allowBlock)
            {
                diff = close.length() - offset;
            }

            preview.append(open);
            for (int i = open.length() + 1; i <= options.getLenBefore() - diff - post.length(); i++)
            {
                preview.append(filler);
            }

            preview.append(post);

            preview.append('\n');
        }
        else if (isBlock)
        {
            preview.append(open).append('\n');
        }

        if (template.length() > 0)
        {
            String[] lines = template.split("\n", -1);
            for (String line : lines)
            {
                preview.append(leader).append(pre);
                int len = 0;
                if (pre.length() > 0 && line.length() > 0)
                {
                    preview.append(' ');
                    len++;
                }
                preview.append(line);
                len += line.length() + leader.length() + pre.length();
                if (isBox && len < options.getLenBefore() - diff)
                {
                    for (; len < options.getLenBefore() - diff - post.length(); len++)
                    {
                        preview.append(' ');
                    }
                    if (isBlock || allowLine)
                    {
                        preview.append(post.substring(0, options.getLenBefore() - diff - len));
                    }
                }

                if (!isBlock && !allowLine)
                {
                    if (preview.charAt(preview.length() - 1) != ' ')
                    {
                        preview.append(' ');
                    }
                    preview.append(close);
                }

                preview.append('\n');
            }
        }

        preview.append(leader);
        if (options.isSeparateAfter())
        {
            preview.append(pre);
            for (int i = leader.length() + pre.length(); i < options.getLenAfter() - close.length(); i++)
            {
                preview.append(filler);
            }
            preview.append(close);
            preview.append('\n');
        }
        else if (isBlock)
        {
          if (!allowBlock) {
            preview.append(pre).append('\n');
          } else {
            preview.append(close).append('\n');
          }
        }

        return preview.substring(0, preview.length() - 1);
    }

    public boolean isSupportedFile(VirtualFile file)
    {
        if (file == null || file.isDirectory())
        {
            return false;
        }

        FileType type = FileTypeManager.getInstance().getFileTypeByFile(file);

        return types.get(type.getName()) != null;
    }

    public boolean isSupportedFile(PsiFile file)
    {
        if (file == null || file instanceof PsiDirectory)
        {
            return false;
        }

        return isSupportedType(file.getFileType());
    }

    public FileType[] getSupportedTypes()
    {
        return new HashSet<FileType>(types.values()).toArray(new FileType[]{});
    }

    public FileType getFileTypeByFile(VirtualFile file)
    {
        FileType type = FileTypeManager.getInstance().getFileTypeByFile(file);

        return getFileTypeByType(type);
    }

    public FileType getFileTypeByType(FileType type)
    {
        return types.get(type.getName());
    }

    public String getFileTypeNameByName(String name)
    {
        FileType type = types.get(name);

        return type != null ? type.getName() : name;
    }

    public static boolean hasBlockComment(FileType fileType)
    {
        Commenter commenter = getCommenter(fileType);

        return commenter != null && commenter.getBlockCommentPrefix() != null;
    }

    public static boolean hasLineComment(FileType fileType)
    {
        Commenter commenter = getCommenter(fileType);

        return commenter != null && commenter.getLineCommentPrefix() != null;
    }

    public boolean allowSeparators(FileType fileType)
    {
        FileType type = getFileTypeByType(fileType);

        return !noSeparators.contains(type);
    }

    private FileTypeUtil()
    {
        createMappings();
        loadFileTypes();
        FileTypeManager.getInstance().addFileTypeListener(new FileTypeListener()
        {
            public void beforeFileTypesChanged(FileTypeEvent fileTypeEvent)
            {
            }

            public void fileTypesChanged(FileTypeEvent fileTypeEvent)
            {
                loadFileTypes();
            }
        });
    }

    private static Commenter getCommenter(FileType fileType)
    {
        if (fileType instanceof LanguageFileType)
        {
          return LanguageCommenters.INSTANCE.forLanguage(((LanguageFileType) fileType).getLanguage());
        }


        return null;
    }

    private void createMappings()
    {
        Set<FileType> maps = new HashSet<FileType>();
        maps.add(StdFileTypes.DTD);
        maps.add(StdFileTypes.XML);

        mappings.put(StdFileTypes.XML, maps);

        maps = new HashSet<FileType>();
        maps.add(StdFileTypes.HTML);
        maps.add(StdFileTypes.XHTML);

        mappings.put(StdFileTypes.HTML, maps);

        maps = new HashSet<FileType>();
        maps.add(StdFileTypes.JSP);

        mappings.put(StdFileTypes.JSP, maps);

        noSeparators.add(StdFileTypes.XML);
        noSeparators.add(StdFileTypes.HTML);
        noSeparators.add(StdFileTypes.JSP);
        noSeparators.add(StdFileTypes.JSPX);

    }

    private static boolean isSupportedType(FileType type)
    {
        if (type.isBinary() || type.getName().indexOf("IDEA") >= 0 || "GUI_DESIGNER_FORM".equals(type.getName()))
        {
            return false;
        }
        else
        {
            Commenter commenter = getCommenter(type);
            boolean hasComment = commenter != null &&
                (commenter.getLineCommentPrefix() != null || commenter.getBlockCommentPrefix() != null);
            if (!hasComment)
            {
                return false;
            }
            else
            {
                if (type.equals(StdFileTypes.JAVA))
                {
                    return true;
                }
                else if (type.equals(StdFileTypes.XML))
                {
                    return true;
                }
                else if (type.equals(StdFileTypes.DTD))
                {
                    return true;
                }
                else if (type.equals(StdFileTypes.HTML))
                {
                    return true;
                }
                else if (type.equals(StdFileTypes.XHTML))
                {
                    return true;
                }
                else if (type.equals(StdFileTypes.JSP))
                {
                    return true;
                }
                else if (type.equals(StdFileTypes.JSPX))
                {
                    return true;
                }
                else if (type.equals(StdFileTypes.PROPERTIES))
                {
                    return true;
                }
                else if ("JavaScript".equals(type.getName()))
                {
                    return true;
                }
            }
            return CopyrightUpdaters.INSTANCE.forFileType(type) != null;
        }
    }

    private void loadFileTypes()
    {
        logger.debug("loadFileTypes");
        types.clear();
        FileType[] ftypes = FileTypeManager.getInstance().getRegisteredFileTypes();
        for (FileType ftype : ftypes)
        {
            // Ignore binary files
            // Ignore IDEA specific file types (PROJECT, MODULE, WORKSPACE)
            // Ignore GUI Designer files
            if (isSupportedType(ftype))
            {
                logger.debug("adding " + ftype.getName());
                Iterator<FileType> iter = mappings.keySet().iterator();
                FileType type = ftype;
                while (iter.hasNext())
                {
                    FileType fileType = iter.next();
                    Set<FileType> maps = mappings.get(fileType);
                    if (maps.contains(ftype))
                    {
                        type = fileType;
                        break;
                    }
                }
                types.put(ftype.getName(), type);
            }
            else
            {
                logger.debug("ignoring " + ftype.getName());
            }
        }
    }

    public static class SortByName implements Comparator<FileType>
    {
        public int compare(FileType a, FileType b)
        {
            return a.getName().compareToIgnoreCase(b.getName());
        }
    }

    private final Map<String, FileType> types = new HashMap<String, FileType>();
    private static FileTypeUtil instance;
    private final Map<FileType, Set<FileType>> mappings = new HashMap<FileType, Set<FileType>>();
    private final Set<FileType> noSeparators = new HashSet<FileType>();

    private static final Logger logger = Logger.getInstance(FileTypeUtil.class.getName());
}