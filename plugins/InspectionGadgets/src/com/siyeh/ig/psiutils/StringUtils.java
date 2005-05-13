package com.siyeh.ig.psiutils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.HashSet;

public class StringUtils{
    private static final Set<String> keywordTable = new HashSet<String>();

    static
    {
        keywordTable.add("abstract");
        keywordTable.add("assert");
        keywordTable.add("boolean");
        keywordTable.add("break");
        keywordTable.add("byte");
        keywordTable.add("case");
        keywordTable.add("catch");
        keywordTable.add("class");
        keywordTable.add("continue");
        keywordTable.add("const");
        keywordTable.add("default");
        keywordTable.add("do");
        keywordTable.add("double");
        keywordTable.add("enum");
        keywordTable.add("else");
        keywordTable.add("extends");
        keywordTable.add("false");
        keywordTable.add("final");
        keywordTable.add("finally");
        keywordTable.add("float");
        keywordTable.add("for");
        keywordTable.add("goto");
        keywordTable.add("implements");
        keywordTable.add("instanceof");
        keywordTable.add("int");
        keywordTable.add("interface");
        keywordTable.add("if");
        keywordTable.add("import");
        keywordTable.add("long");
        keywordTable.add("native");
        keywordTable.add("new");
        keywordTable.add("null");
        keywordTable.add("package");
        keywordTable.add("private");
        keywordTable.add("protected");
        keywordTable.add("public");
        keywordTable.add("return");
        keywordTable.add("short");
        keywordTable.add("static");
        keywordTable.add("strictfp");
        keywordTable.add("super");
        keywordTable.add("switch");
        keywordTable.add("synchronized");
        keywordTable.add("throw");
        keywordTable.add("throws");
        keywordTable.add("transient");
        keywordTable.add("true");
        keywordTable.add("try");
        keywordTable.add("void");
        keywordTable.add("volatile");
        keywordTable.add("while");
    }

    private StringUtils(){
        super();
    }

    public static String capitalize(@NotNull String name){
        final char startChar = name.charAt(0);
        if(Character.isUpperCase(startChar)){
            return name;
        } else{
            return Character.toUpperCase(startChar) + name.substring(1);
        }
    }

    public static @NotNull String stripPrefixAndSuffix(@NotNull String name,
                                              @Nullable String prefix,
                                              @Nullable String suffix){
        String strippedName = name;
        if(prefix != null){
            final int prefixLength = prefix.length();
            if(prefixLength != 0 && strippedName.startsWith(prefix)){
                strippedName = strippedName.substring(prefixLength);
            }
        }
        if(suffix != null){
            final int suffixLength = suffix.length();
            if(suffixLength != 0 && strippedName.startsWith(suffix)){
                final int newNameLength = strippedName.length() - suffixLength;
                strippedName = strippedName.substring(0, newNameLength);
            }
        }
        return strippedName;
    }

    public static @NotNull String createSingularFromName(@NotNull String name){
        final String singularName;
        final int nameLength = name.length();
        if(name.endsWith("ies")){
            singularName = name.substring(0, nameLength - 3) + 'y';
        } else if(name.endsWith("sses") || name.endsWith("shes")){
            singularName = name.substring(0, nameLength - 2);
        } else if(name.charAt(nameLength - 1) == 's'){
            singularName = name.substring(0, nameLength - 1);
        } else{
            singularName = 'a' + capitalize(name);
        }
        if(isKeyword(singularName)){
            return 'a' + capitalize(singularName);
        } else{
            return singularName;
        }
    }

    private static boolean isKeyword(String name){
        return keywordTable.contains(name);
    }
}