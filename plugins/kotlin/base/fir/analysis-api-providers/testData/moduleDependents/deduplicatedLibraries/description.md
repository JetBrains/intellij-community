Different libraries with the same roots are deduplicated by `LibraryInfoCache` into the same `LibraryInfo`. As explained in `LibraryInfo`'s 
documentation, a library info and by extension a `KtLibraryModule` should be considered a *collection* of `LibraryEx`s. This test ensures 
that `IdeKotlinModuleDependentsProvider` provides the correct dependents for such "collective" `LibraryInfo`s. It is a bit unintuitive that
`L1` and `L2` have the same dependents, but it's the correct result when viewing `LibraryInfo`s as a collection.

Furthermore, the test ensures that another library `L3` with partially matching roots is not considered in the calculation of the dependents
of `L1` and `L2`.
