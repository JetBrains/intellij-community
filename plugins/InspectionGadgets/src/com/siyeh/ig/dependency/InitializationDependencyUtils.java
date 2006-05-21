package com.siyeh.ig.dependency;

import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.util.Key;

import java.util.*;

@SuppressWarnings({"MethodWithMultipleLoops"})
public class InitializationDependencyUtils {
    private static final Key<Set<RefClass>> INITIALIZATION_DEPENDENT_CLASSES_KEY =
            new Key<Set<RefClass>>("INITIALIZATION_DEPENDENT_CLASSES");
    private static final Key<Set<RefClass>> INITIALIZATION_DEPENDENCY_CLASSES_KEY =
            new Key<Set<RefClass>>("INITIALIZATION_DEPENDENT_CLASSES");
    private static final Key<Set<RefClass>> TRANSITIVE_INITIALIZATION_DEPENDENT_CLASSES_KEY =
            new Key<Set<RefClass>>("TRANSITIVE_INITIALIZATION_DEPENDENT_CLASSES_KEY");
    private static final Key<Set<RefClass>> TRANSITIVE_INITIALIZATION_DEPENDENCY_CLASSES_KEY =
            new Key<Set<RefClass>>("TRANSITIVE_INITIALIZATION_DEPENDENCY_CLASSES_KEY");

    private InitializationDependencyUtils() {
    }

    public static Set<RefClass> calculateInitializationDependenciesForClass(RefClass refClass) {
        final Set<RefClass> dependencies =
                refClass.getUserData(INITIALIZATION_DEPENDENCY_CLASSES_KEY);
        if (dependencies != null) {
            return dependencies;
        }
        final Set<RefClass> newDependencies = new HashSet<RefClass>();
        tabulateInitializationDependencyClasses(refClass, newDependencies);
        newDependencies.remove(refClass);
        refClass.putUserData(INITIALIZATION_DEPENDENCY_CLASSES_KEY, newDependencies);
        return newDependencies;
    }

    @SuppressWarnings({"MethodWithMultipleLoops"})
    static void tabulateInitializationDependencyClasses(RefElement element, Set<RefClass> dependencies) {
        final Collection<RefElement> references = element.getOutReferences();
        final RefUtil refUtil = RefUtil.getInstance();
        for (RefElement reference : references) {
            final RefClass refClass = refUtil.getTopLevelClass(reference);
            if (refClass != null) {
                dependencies.add(refClass);
            }
        }
        final List<RefEntity> children = element.getChildren();
        if (children == null) {
            return;
        }
        for (RefEntity child : children) {
            if (child instanceof RefElement) {
                tabulateInitializationDependencyClasses((RefElement) child, dependencies);
            }
        }
    }

    public static Set<RefClass> calculateTransitiveInitializationDependenciesForClass(RefClass refClass) {
        final Set<RefClass> dependencies =
                refClass.getUserData(TRANSITIVE_INITIALIZATION_DEPENDENCY_CLASSES_KEY);
        if (dependencies != null) {
            return dependencies;
        }
        final Set<RefClass> newDependencies = new HashSet<RefClass>();
        tabulateTransitiveInitializationDependencyClasses(refClass, newDependencies);
        refClass.putUserData(TRANSITIVE_INITIALIZATION_DEPENDENCY_CLASSES_KEY, newDependencies);
        return newDependencies;
    }

    private static void tabulateTransitiveInitializationDependencyClasses(RefClass refClass,
                                                            Set<RefClass> newDependencies) {
        final LinkedList<RefClass> pendingClasses = new LinkedList<RefClass>();
        final Set<RefClass> processedClasses = new HashSet<RefClass>();
        pendingClasses.addLast(refClass);
        while (!pendingClasses.isEmpty()) {
            final RefClass classToProcess = pendingClasses.removeFirst();
            newDependencies.add(classToProcess);
            processedClasses.add(classToProcess);
            final Set<RefClass> dependencies =
                    calculateInitializationDependenciesForClass(classToProcess);
            for (RefClass dependency : dependencies) {
                if (!pendingClasses.contains(dependency) &&
                        !processedClasses.contains(dependency)) {
                    pendingClasses.addLast(dependency);
                }
            }
        }
        newDependencies.remove(refClass);
    }


    public static Set<RefClass> calculateInitializationDependentsForClass(RefClass refClass) {
        final Set<RefClass> dependents =
                refClass.getUserData(INITIALIZATION_DEPENDENT_CLASSES_KEY);
        if (dependents != null) {
            return dependents;
        }
        final Set<RefClass> newDependents = new HashSet<RefClass>();
        tabulateInitializationDependentClasses(refClass, newDependents);
        newDependents.remove(refClass);
        refClass.putUserData(INITIALIZATION_DEPENDENT_CLASSES_KEY, newDependents);
        return newDependents;
    }

    @SuppressWarnings({"MethodWithMultipleLoops"})
    private static void tabulateInitializationDependentClasses(RefElement element, Set<RefClass> dependents) {
        final Collection<RefElement> references = element.getInReferences();
        final RefUtil refUtil = RefUtil.getInstance();
        for (RefElement reference : references) {
            final RefClass refClass = refUtil.getTopLevelClass(reference);
            if (refClass != null) {
                dependents.add(refClass);
            }
        }
        final List<RefEntity> children = element.getChildren();
        if (children == null) {
            return;
        }
        for (RefEntity child : children) {
            if (child instanceof RefElement) {
                tabulateInitializationDependentClasses((RefElement) child, dependents);
            }
        }
    }

    public static Set<RefClass> calculateTransitiveInitializationDependentsForClass(RefClass refClass) {
        final Set<RefClass> dependents =
                refClass.getUserData(TRANSITIVE_INITIALIZATION_DEPENDENT_CLASSES_KEY);
        if (dependents != null) {
            return dependents;
        }
        final Set<RefClass> newDependents = new HashSet<RefClass>();
        tabulateInitializationTransitiveDependentClasses(refClass, newDependents);
        refClass.putUserData(TRANSITIVE_INITIALIZATION_DEPENDENT_CLASSES_KEY, newDependents);
        return newDependents;
    }

    private static void tabulateInitializationTransitiveDependentClasses(RefClass refClass,
                                                                         Set<RefClass> newDependents) {
        final LinkedList<RefClass> pendingClasses = new LinkedList<RefClass>();
        final Set<RefClass> processedClasses = new HashSet<RefClass>();
        pendingClasses.addLast(refClass);
        while (!pendingClasses.isEmpty()) {
            final RefClass classToProcess = pendingClasses.removeFirst();
            newDependents.add(classToProcess);
            processedClasses.add(classToProcess);
            final Set<RefClass> dependents = calculateInitializationDependentsForClass(classToProcess);
            for (RefClass dependent : dependents) {
                if (!pendingClasses.contains(dependent) &&
                        !processedClasses.contains(dependent)) {
                    pendingClasses.addLast(dependent);
                }
            }
        }
        newDependents.remove(refClass);
    }
}