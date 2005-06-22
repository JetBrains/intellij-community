package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CollectionUtils{
    /**
     * @noinspection StaticCollection
     */
    private static final Set<String> s_collectionClassesRequiringCapacity =
             new HashSet<String>(10);
    /**
     * @noinspection StaticCollection
     */
    private static final Set<String> s_allCollectionClasses = new HashSet<String>(10);
    /**
     * @noinspection StaticCollection
     */
    private static final Set<String> s_allCollectionClassesAndInterfaces =
            new HashSet<String>(10);
    /**
     * @noinspection StaticCollection
     */
    private static final Map<String,String> s_interfaceForCollection = new HashMap<String, String>(10);

    static {
        s_collectionClassesRequiringCapacity.add("java.util.BitSet");
        s_collectionClassesRequiringCapacity.add("java.util.Vector");
        s_collectionClassesRequiringCapacity.add("java.util.ArrayList");
        s_collectionClassesRequiringCapacity.add("java.util.HashMap");
        s_collectionClassesRequiringCapacity.add("java.util.LinkedHashMap");
        s_collectionClassesRequiringCapacity.add("java.util.WeakHashMap");
        s_collectionClassesRequiringCapacity.add("java.util.Hashtable");
        s_collectionClassesRequiringCapacity.add("java.util.HashSet");
        s_collectionClassesRequiringCapacity.add("java.util.LinkedHashSet");
        s_collectionClassesRequiringCapacity.add("com.sun.java.util.collections.BitSet");
        s_collectionClassesRequiringCapacity.add("com.sun.java.util.collections.Vector");
        s_collectionClassesRequiringCapacity.add("com.sun.java.util.collections.ArrayList");
        s_collectionClassesRequiringCapacity.add("com.sun.java.util.collections.HashMap");
        s_collectionClassesRequiringCapacity.add("com.sun.java.util.collections.Hashtable");
        s_collectionClassesRequiringCapacity.add("com.sun.java.util.collections.HashSet");

        s_allCollectionClasses.add("java.util.Vector");
        s_allCollectionClasses.add("java.util.ArrayList");
        s_allCollectionClasses.add("java.util.LinkedList");
        s_allCollectionClasses.add("java.util.HashMap");
        s_allCollectionClasses.add("java.util.IdentityHashMap");
        s_allCollectionClasses.add("java.util.LinkedHashMap");
        s_allCollectionClasses.add("java.util.WeakHashMap");
        s_allCollectionClasses.add("java.util.Hashtable");
        s_allCollectionClasses.add("java.util.HashSet");
        s_allCollectionClasses.add("java.util.LinkedHashSet");
        s_allCollectionClasses.add("java.util.TreeSet");
        s_allCollectionClasses.add("java.util.TreeMap");
        s_allCollectionClasses.add("java.util.EnumSet");
        s_allCollectionClasses.add("java.util.EnumMap");
        s_allCollectionClasses.add("com.sun.java.util.collections.Vector");
        s_allCollectionClasses.add("com.sun.java.util.collections.ArrayList");
        s_allCollectionClasses.add("com.sun.java.util.collections.LinkedList");
        s_allCollectionClasses.add("com.sun.java.util.collections.HashMap");
        s_allCollectionClasses.add("com.sun.java.util.collections.Hashtable");
        s_allCollectionClasses.add("com.sun.java.util.collections.HashSet");
        s_allCollectionClasses.add("com.sun.java.util.collections.TreeSet");
        s_allCollectionClasses.add("com.sun.java.util.collections.TreeMap");

        s_allCollectionClassesAndInterfaces.add("java.util.Collection");
        s_allCollectionClassesAndInterfaces.add("java.util.Vector");
        s_allCollectionClassesAndInterfaces.add("java.util.ArrayList");
        s_allCollectionClassesAndInterfaces.add("java.util.LinkedList");
        s_allCollectionClassesAndInterfaces.add("java.util.HashMap");
        s_allCollectionClassesAndInterfaces.add("java.util.LinkedHashMap");
        s_allCollectionClassesAndInterfaces.add("java.util.IdentityHashMap");
        s_allCollectionClassesAndInterfaces.add("java.util.WeakHashMap");
        s_allCollectionClassesAndInterfaces.add("java.util.Hashtable");
        s_allCollectionClassesAndInterfaces.add("java.util.HashSet");
        s_allCollectionClassesAndInterfaces.add("java.util.LinkedHashSet");
        s_allCollectionClassesAndInterfaces.add("java.util.TreeSet");
        s_allCollectionClassesAndInterfaces.add("java.util.TreeMap");
        s_allCollectionClassesAndInterfaces.add("java.util.Set");
        s_allCollectionClassesAndInterfaces.add("java.util.Map");
        s_allCollectionClassesAndInterfaces.add("java.util.List");
        s_allCollectionClassesAndInterfaces.add("java.util.SortedMap");
        s_allCollectionClassesAndInterfaces.add("java.util.SortedSet");
        s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.Collection");
        s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.Vector");
        s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.ArrayList");
        s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.LinkedList");
        s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.HashMap");
        s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.Hashtable");
        s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.HashSet");
        s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.TreeSet");
        s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.TreeMap");
        s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.Set");
        s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.Map");
        s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.List");
        s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.SortedMap");
        s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.SortedSet");

        s_interfaceForCollection.put("HashSet", "Set");
        s_interfaceForCollection.put("LinkedHashSet", "Set");
        s_interfaceForCollection.put("TreeSet", "SortedSet");
        s_interfaceForCollection.put("Vector", "List");
        s_interfaceForCollection.put("ArrayList", "List");
        s_interfaceForCollection.put("LinkedList", "List");
        s_interfaceForCollection.put("TreeMap", "Map");
        s_interfaceForCollection.put("HashMap", "Map");
        s_interfaceForCollection.put("IdentityHashMap", "Map");
        s_interfaceForCollection.put("LinkedHashMap", "Map");
        s_interfaceForCollection.put("WeakHashMap", "Map");
        s_interfaceForCollection.put("Hashtable", "Map");
        s_interfaceForCollection.put("EnumSet", "Set");
        s_interfaceForCollection.put("EnumMap", "Map");
        s_interfaceForCollection.put("java.util.HashSet", "java.util.Set");
        s_interfaceForCollection.put("java.util.LinkedHashSet",
                                     "java.util.Set");
        s_interfaceForCollection.put("java.util.TreeSet", "java.util.Set");
        s_interfaceForCollection.put("java.util.Vector", "java.util.List");
        s_interfaceForCollection.put("java.util.ArrayList", "java.util.List");
        s_interfaceForCollection.put("java.util.TreeMap", "java.util.Map");
        s_interfaceForCollection.put("java.util.HashMap", "java.util.Map");
        s_interfaceForCollection.put("java.util.IdentityHashMap",
                                     "java.util.Map");
        s_interfaceForCollection.put("java.util.LinkedHashMap",
                                     "java.util.Map");
        s_interfaceForCollection.put("java.util.WeakHashMap", "java.util.Map");
        s_interfaceForCollection.put("java.util.Hashtable", "java.util.Map");
        s_interfaceForCollection.put("java.util.EnumSet", "java.util.Set");
        s_interfaceForCollection.put("java.util.EnumMap", "java.util.Map");
        s_interfaceForCollection.put("com.sun.java.util.collections.HashSet",
                                     "com.sun.java.util.collections.Set");
        s_interfaceForCollection.put("com.sun.java.util.collections.TreeSet",
                                     "com.sun.java.util.collections.Set");
        s_interfaceForCollection.put("com.sun.java.util.collections.Vector",
                                     "com.sun.java.util.collections.List");
        s_interfaceForCollection.put("com.sun.java.util.collections.ArrayList",
                                     "com.sun.java.util.collections.List");
        s_interfaceForCollection.put("com.sun.java.util.collections.LinkedList",
                                     "com.sun.java.util.collections.List");
        s_interfaceForCollection.put("com.sun.java.util.collections.TreeMap",
                                     "com.sun.java.util.collections.Map");
        s_interfaceForCollection.put("com.sun.java.util.collections.HashMap",
                                     "com.sun.java.util.collections.Map");
        s_interfaceForCollection.put("com.sun.java.util.collections.Hashtable",
                                     "com.sun.java.util.collections.Map");
    }

    private CollectionUtils(){
        super();
    }

    public static boolean isCollectionWithInitialCapacity(@Nullable PsiType type){
        if(!(type instanceof PsiClassType)) {
            return false;
        }
        final PsiClassType classType = (PsiClassType) type;
        final PsiClass resolved = classType.resolve();
        if(resolved == null) {
            return false;
        }
        final String className = resolved.getQualifiedName();
        return s_collectionClassesRequiringCapacity.contains(className);
    }

    public static boolean isCollectionClass(@Nullable PsiType type){
        if(!(type instanceof PsiClassType)) {
            return false;
        }
        final PsiClassType classType = (PsiClassType) type;
        final PsiClass resolved = classType.resolve();
        if(resolved == null) {
            return false;
        }
        return isCollectionClass(resolved);
    }

    public static boolean isCollectionClass(final PsiClass aClass){
        final String className = aClass.getQualifiedName();
        return s_allCollectionClasses.contains(className);
    }

    public static boolean isCollectionClassOrInterface(@Nullable PsiType type){
        if(!(type instanceof PsiClassType)) {
            return false;
        }
        final PsiClassType classType = (PsiClassType) type;
        final PsiClass resolved = classType.resolve();
        if(resolved == null) {
            return false;
        }
        final String className = resolved.getQualifiedName();
        return s_allCollectionClassesAndInterfaces.contains(className);
    }
    public static boolean isWeakCollectionClass(@Nullable PsiType type){
        if(!(type instanceof PsiClassType)){
            return false;
        }
        final String typeText = type.getCanonicalText();
        if(typeText == null)
        {
            return false;
        }
        return "java.util.WeakHashMap".equals(typeText);
    }

    public static boolean isConstantArrayOfZeroSize(@NotNull PsiField field){
        if(!field.hasModifierProperty(PsiModifier.STATIC) ||
                !field.hasModifierProperty(PsiModifier.FINAL)) {
            return false;
        }
        final PsiExpression initializer = field.getInitializer();
        if(!(initializer instanceof PsiNewExpression)) {
            return false;
        }
        final PsiNewExpression expression = (PsiNewExpression) initializer;
        final PsiExpression[] dimensions = expression.getArrayDimensions();
        if(dimensions != null) {
            if(dimensions.length != 1) {
                return false;
            }
            final PsiExpression dimension = dimensions[0];
            final String dimensionText = dimension.getText();
            if("0".equals(dimensionText)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isArrayOrCollectionField(@Nullable PsiExpression value){
        if(!(value instanceof PsiReferenceExpression)) {
            return false;
        }
        final PsiReferenceExpression fieldReference =
                (PsiReferenceExpression) value;

        final PsiElement element = fieldReference.resolve();
        if(!(element instanceof PsiField)) {
            return false;
        }
        final PsiType type = fieldReference.getType();
        if(type == null) {
            return false;
        }
        if(type.getArrayDimensions() > 0) {
            return !isConstantArrayOfZeroSize((PsiField) element);
        }
        return isCollectionClassOrInterface(type);
    }

    public static String getInterfaceForClass(String name){
        final int paramStart = name.indexOf((int) '<');
        String baseName;
        final String arg;
        if(paramStart >= 0) {
            baseName = name.substring(0, paramStart);
            baseName = baseName.trim();
            arg = name.substring(paramStart);
        } else{
            baseName = name;
            arg = "";
        }
        return s_interfaceForCollection.get(baseName) + arg;
    }
}
