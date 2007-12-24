package com.maddyhome.idea.copyright.util;

/*
 * Copyright - Copyright notice updater for IDEA
 * Copyright (C) 2004-2005 Rick Maddy. All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.xml.XmlFile;
import com.maddyhome.idea.copyright.options.TemplateOptions;

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

    public static String buildComment(FileType type, boolean useAlternate, String template, TemplateOptions options)
    {
        FileType fileType = type;
        if (useAlternate)
        {
            FileType alternate = getInstance().getAlternate(type);
            if (alternate != null)
            {
                fileType = alternate;
            }
        }

        Commenter commenter = getCommenter(fileType);
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

        boolean allowSeparator = getInstance().allowSeparators(fileType);
        char filler = options.getFiller();
        if (!allowSeparator)
        {
            if (options.getFiller() == TemplateOptions.DEFAULT_FILLER)
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
        if (filler == TemplateOptions.DEFAULT_FILLER)
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

        if (options.isSeparateAfter())
        {
            preview.append(leader).append(pre);
            for (int i = leader.length() + pre.length(); i < options.getLenAfter() - close.length(); i++)
            {
                preview.append(filler);
            }
            preview.append(close);
            preview.append('\n');
        }
        else if (isBlock)
        {
            preview.append(leader).append(close).append('\n');
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

        FileType type = file.getFileType();

        FileType match = types.get(type.getName());
        if (match != null)
        {
            if (type.equals(StdFileTypes.JAVA) && !(file instanceof PsiJavaFile))
            {
                return false;
            }
            if (type.equals(StdFileTypes.XML) && !(file instanceof XmlFile))
            {
                return false;
            }
            return !(type.equals(StdFileTypes.JSP) && !(file instanceof JspFile));
        }

        return false;
    }

    public FileType[] getSupportedTypes()
    {
        return new HashSet<FileType>(types.values()).toArray(new FileType[]{});
    }

    public String[] getMappedNames(FileType type)
    {
        TreeSet<String> names = new TreeSet<String>();
        for (String name : types.keySet())
        {
            FileType mapped = types.get(name);
            if (mapped.equals(type))
            {
                names.add(name);
            }
        }

        return names.toArray(new String[]{});
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

    public FileType getAlternate(FileType fileType)
    {
        return alternates.get(fileType);
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
        maps.add(StdFileTypes.JSPX);

        mappings.put(StdFileTypes.JSP, maps);

        noSeparators.add(StdFileTypes.XML);
        noSeparators.add(StdFileTypes.HTML);
        noSeparators.add(StdFileTypes.JSP);

        alternates.put(StdFileTypes.JSP, StdFileTypes.XML);
    }

    private boolean isSupportedType(FileType type)
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
                else
                {
                    if (type instanceof LanguageFileType)
                    {
                        Language lang = ((LanguageFileType)type).getLanguage();
                        if (lang.equals(StdLanguages.CSS))
                        {
                            return true;
                        }
                    }
                }
            }

            return false;
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

    private Map<String, FileType> types = new HashMap<String, FileType>();
    private static FileTypeUtil instance;
    private Map<FileType, Set<FileType>> mappings = new HashMap<FileType, Set<FileType>>();
    private Set<FileType> noSeparators = new HashSet<FileType>();
    private Map<FileType, FileType> alternates = new HashMap<FileType, FileType>();

    private static Logger logger = Logger.getInstance(FileTypeUtil.class.getName());
}