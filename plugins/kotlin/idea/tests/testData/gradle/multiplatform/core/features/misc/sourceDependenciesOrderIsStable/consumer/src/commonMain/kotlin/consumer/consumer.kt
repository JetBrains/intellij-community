package consumer

import test.ClashingClass

/**
 * ATTENTION
 *
 * If this test is flaky, then it means that the order of dependencies IS NOT PRESERVED
 * somewhere between KGP and Frontend.
 *
 * Order of dependencies MUST be stable, because it's the intended way of solving
 * classpath-hells a.k.a. version clashes: if there's two libraries with clashing FQNs,
 * on a classpath, a user is supposed to explicitly add the dependency that they want to be used
 *
 * IF ORDER IS CHANGED, THEN THIS A SIGN OF A BUG. PLEASE DON'T APPLY CHANGES TO TESTDATA
 * WITHOUT INVESTIGATING THE CAUSES
 *
 * Pro tip: most popular reason for losing the order of depenedncies is accidental usage
 * of unordered collections (like HashSet)
 */
fun use(c: ClashingClass) {
    // Expected to be resolved, as the dependency to lib2 is delcared first
    c.apiOfLib2()

    // Expected to be unresolved, as the dependency to lib1 is delcared second
    c.<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: apiOfLib1'")!>apiOfLib1<!>()
}
