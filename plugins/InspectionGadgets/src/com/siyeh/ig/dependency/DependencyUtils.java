package com.siyeh.ig.dependency;

import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.util.Key;

import java.util.*;

@SuppressWarnings({"MethodWithMultipleLoops"})
public class DependencyUtils {
    private static final Key<Set<RefClass>> DEPENDENT_CLASSES_KEY = new Key<Set<RefClass>>("DEPENDENT_CLASSES");
    private static final Key<Set<RefClass>> DEPENDENCY_CLASSES_KEY = new Key<Set<RefClass>>("DEPENDENCY_CLASSES");
    private static final Key<Set<RefClass>> TRANSITIVE_DEPENDENT_CLASSES_KEY = new Key<Set<RefClass>>(
            "TRANSITIVE_DEPENDENT_CLASSES");
    private static final Key<Set<RefClass>> TRANSITIVE_DEPENDENCY_CLASSES_KEY = new Key<Set<RefClass>>(
            "TRANSITIVE_DEPENDENCY_CLASSES");

    private static final Key<Set<RefPackage>> DEPENDENT_PACKAGES_KEY = new Key<Set<RefPackage>>("DEPENDENT_PACKAGES");
    private static final Key<Set<RefPackage>> DEPENDENCY_PACKAGES_KEY = new Key<Set<RefPackage>>("DEPENDENCY_PACKAGES");
    private static final Key<Set<RefPackage>> TRANSITIVE_DEPENDENT_PACKAGES_KEY = new Key<Set<RefPackage>>(
            "TRANSITIVE_DEPENDENT_PACKAGES");
    private static final Key<Set<RefPackage>> TRANSITIVE_DEPENDENCY_PACKAGES_KEY = new Key<Set<RefPackage>>(
            "TRANSITIVE_DEPENDENCY_PACKAGES");

    private DependencyUtils() {
    }

    public static Set<RefClass> calculateDependenciesForClass(RefClass refClass) {
        final Set<RefClass> dependencies = refClass.getUserData(DEPENDENCY_CLASSES_KEY);
        if (dependencies != null) {
            return dependencies;
        }
        final Set<RefClass> newDependencies = new HashSet<RefClass>();
        tabulateDependencyClasses(refClass, newDependencies);
        newDependencies.remove(refClass);
        refClass.putUserData(DEPENDENCY_CLASSES_KEY, newDependencies);
        return newDependencies;
    }

    @SuppressWarnings({"MethodWithMultipleLoops"})
    static void tabulateDependencyClasses(RefElement element, Set<RefClass> dependencies) {
        final Collection<RefElement> references = element.getOutReferences();
        final RefUtil refUtil = RefUtil.getInstance();
        for (RefElement reference : references) {
            final RefClass refClass = refUtil.getTopLevelClass(reference);
            if (refClass != null) {
                dependencies.add(refClass);
            }
        }
        final Collection<RefClass> typeReferences = element.getOutTypeReferences();
        for (RefElement reference : typeReferences) {
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
                tabulateDependencyClasses((RefElement) child, dependencies);
            }
        }
    }

    public static Set<RefClass> calculateTransitiveDependenciesForClass(RefClass refClass) {
        final Set<RefClass> dependencies = refClass.getUserData(TRANSITIVE_DEPENDENCY_CLASSES_KEY);
        if (dependencies != null) {
            return dependencies;
        }
        final Set<RefClass> newDependencies = new HashSet<RefClass>();
        tabulateTransitiveDependencyClasses(refClass, newDependencies);
        refClass.putUserData(TRANSITIVE_DEPENDENCY_CLASSES_KEY, newDependencies);
        return newDependencies;
    }

    private static void tabulateTransitiveDependencyClasses(RefClass refClass, Set<RefClass> newDependencies) {
        final LinkedList<RefClass> pendingClasses = new LinkedList<RefClass>();
        final Set<RefClass> processedClasses = new HashSet<RefClass>();
        pendingClasses.addLast(refClass);
        while (!pendingClasses.isEmpty()) {
            final RefClass classToProcess = pendingClasses.removeFirst();
            newDependencies.add(classToProcess);
            processedClasses.add(classToProcess);
            final Set<RefClass> dependencies = calculateDependenciesForClass(classToProcess);
            for (RefClass dependency : dependencies) {
                if (!pendingClasses.contains(dependency) && !processedClasses.contains(dependency)) {
                    pendingClasses.addLast(dependency);
                }
            }
        }
        newDependencies.remove(refClass);
    }

    public static Set<RefClass> calculateDependentsForClass(RefClass refClass) {
        final Set<RefClass> dependents = refClass.getUserData(DEPENDENT_CLASSES_KEY);
        if (dependents != null) {
            return dependents;
        }
        final Set<RefClass> newDependents = new HashSet<RefClass>();
        tabulateDependentClasses(refClass, newDependents);
        final Set<RefElement> typeReferences = refClass.getInTypeReferences();
        final RefUtil refUtil = RefUtil.getInstance();
        for (RefElement typeReference : typeReferences) {
            final RefClass referencingClass = refUtil.getTopLevelClass(typeReference);
            newDependents.add(referencingClass);
        }
        newDependents.remove(refClass);
        refClass.putUserData(DEPENDENT_CLASSES_KEY, newDependents);
        return newDependents;
    }

    @SuppressWarnings({"MethodWithMultipleLoops"})
    private static void tabulateDependentClasses(RefElement element, Set<RefClass> dependents) {
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
                tabulateDependentClasses((RefElement) child, dependents);
            }
        }
    }

    public static Set<RefClass> calculateTransitiveDependentsForClass(RefClass refClass) {
        final Set<RefClass> dependents = refClass.getUserData(TRANSITIVE_DEPENDENT_CLASSES_KEY);
        if (dependents != null) {
            return dependents;
        }
        final Set<RefClass> newDependents = new HashSet<RefClass>();
        tabulateTransitiveDependentClasses(refClass, newDependents);
        refClass.putUserData(TRANSITIVE_DEPENDENT_CLASSES_KEY, newDependents);
        return newDependents;
    }

    private static void tabulateTransitiveDependentClasses(RefClass refClass, Set<RefClass> newDependents) {
        final LinkedList<RefClass> pendingClasses = new LinkedList<RefClass>();
        final Set<RefClass> processedClasses = new HashSet<RefClass>();
        pendingClasses.addLast(refClass);
        while (!pendingClasses.isEmpty()) {
            final RefClass classToProcess = pendingClasses.removeFirst();
            newDependents.add(classToProcess);
            processedClasses.add(classToProcess);
            final Set<RefClass> dependents = calculateDependentsForClass(classToProcess);
            for (RefClass dependent : dependents) {
                if (!pendingClasses.contains(dependent) && !processedClasses.contains(dependent)) {
                    pendingClasses.addLast(dependent);
                }
            }
        }
        newDependents.remove(refClass);
    }

    public static Set<RefPackage> calculateDependenciesForPackage(RefPackage refPackage) {
        final Set<RefPackage> dependencies = refPackage.getUserData(DEPENDENCY_PACKAGES_KEY);
        if (dependencies != null) {
            return dependencies;
        }
        final Set<RefPackage> newDependencies = new HashSet<RefPackage>();

        tabulateDependencyPackages(refPackage, newDependencies);
        newDependencies.remove(refPackage);
        refPackage.putUserData(DEPENDENCY_PACKAGES_KEY, newDependencies);
        return newDependencies;
    }

    static void tabulateDependencyPackages(RefEntity entity, Set<RefPackage> dependencies) {
        if (entity instanceof RefElement) {
            final RefElement element = (RefElement) entity;
            final Collection<RefElement> references = element.getOutReferences();
            final RefUtil refUtil = RefUtil.getInstance();
            for (RefElement reference : references) {
                final RefPackage refPackage = refUtil.getPackage(reference);
                if (refPackage != null) {
                    dependencies.add(refPackage);
                }
            }
        }
        final List<RefEntity> children = entity.getChildren();
        if (children == null) {
            return;
        }
        for (RefEntity child : children) {
            if (!(child instanceof RefPackage)) {
                tabulateDependencyPackages(child, dependencies);
            }
        }
    }

    public static Set<RefPackage> calculateDependentsForPackage(RefPackage refPackage) {
        final Set<RefPackage> dependents = refPackage.getUserData(DEPENDENT_PACKAGES_KEY);
        if (dependents != null) {
            return dependents;
        }
        final Set<RefPackage> newDependents = new HashSet<RefPackage>();
        tabulateDependentPackages(refPackage, newDependents);
        newDependents.remove(refPackage);
        refPackage.putUserData(DEPENDENT_PACKAGES_KEY, newDependents);
        return newDependents;
    }

    static void tabulateDependentPackages(RefEntity entity, Set<RefPackage> dependents) {
        if (entity instanceof RefElement) {
            final RefElement element = (RefElement) entity;
            final Collection<RefElement> references = element.getOutReferences();
            final RefUtil refUtil = RefUtil.getInstance();
            for (RefElement reference : references) {
                final RefPackage refPackage = refUtil.getPackage(reference);
                if (refPackage != null) {
                    dependents.add(refPackage);
                }
            }
        }
        final List<RefEntity> children = entity.getChildren();
        if (children == null) {
            return;
        }
        for (RefEntity child : children) {
            if (!(child instanceof RefPackage)) {
                tabulateDependentPackages(child, dependents);
            }
        }
    }

    public static Set<RefPackage> calculateTransitiveDependentsForPackage(RefPackage refPackage) {
        final Set<RefPackage> dependents = refPackage.getUserData(TRANSITIVE_DEPENDENT_PACKAGES_KEY);
        if (dependents != null) {
            return dependents;
        }
        final Set<RefPackage> newDependents = new HashSet<RefPackage>();
        tabulateTransitiveDependentPackages(refPackage, newDependents);
        refPackage.putUserData(TRANSITIVE_DEPENDENT_PACKAGES_KEY, newDependents);
        return newDependents;
    }

    private static void tabulateTransitiveDependentPackages(RefPackage refPackage, Set<RefPackage> newDependents) {
        final LinkedList<RefPackage> pendingPackages = new LinkedList<RefPackage>();
        final Set<RefPackage> processedPackages = new HashSet<RefPackage>();
        pendingPackages.addLast(refPackage);
        while (!pendingPackages.isEmpty()) {
            final RefPackage packageToProcess = pendingPackages.removeFirst();
            newDependents.add(packageToProcess);
            processedPackages.add(packageToProcess);
            final Set<RefPackage> dependents = calculateDependentsForPackage(packageToProcess);
            for (RefPackage dependent : dependents) {
                if (!pendingPackages.contains(dependent) && !processedPackages.contains(dependent)) {
                    pendingPackages.addLast(dependent);
                }
            }
        }
        newDependents.remove(refPackage);
    }

    public static Set<RefPackage> calculateTransitiveDependenciesForPackage(RefPackage refPackage) {
        final Set<RefPackage> dependencies = refPackage.getUserData(TRANSITIVE_DEPENDENCY_PACKAGES_KEY);
        if (dependencies != null) {
            return dependencies;
        }
        final Set<RefPackage> newDependencies = new HashSet<RefPackage>();
        tabulateTransitiveDependencyPackages(refPackage, newDependencies);
        refPackage.putUserData(TRANSITIVE_DEPENDENCY_PACKAGES_KEY, newDependencies);
        return newDependencies;
    }

    private static void tabulateTransitiveDependencyPackages(RefPackage refPackage, Set<RefPackage> newDependencies) {
        final LinkedList<RefPackage> pendingPackages = new LinkedList<RefPackage>();
        final Set<RefPackage> processedPackages = new HashSet<RefPackage>();
        pendingPackages.addLast(refPackage);
        while (!pendingPackages.isEmpty()) {
            final RefPackage packageToProcess = pendingPackages.removeFirst();
            newDependencies.add(packageToProcess);
            processedPackages.add(packageToProcess);
            final Set<RefPackage> dependencies = calculateDependenciesForPackage(packageToProcess);
            for (RefPackage dependency : dependencies) {
                if (!pendingPackages.contains(dependency) && !processedPackages.contains(dependency)) {
                    pendingPackages.addLast(dependency);
                }
            }
        }
        newDependencies.remove(refPackage);
    }
}
