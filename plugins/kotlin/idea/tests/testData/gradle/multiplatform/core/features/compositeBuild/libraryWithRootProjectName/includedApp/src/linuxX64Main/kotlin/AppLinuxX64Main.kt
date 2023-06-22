object AppLinuxX64Main {
    init {
        LibraryCommonMain.call()
        LibraryUtilsCommonMain.call()

        LibraryNativeMain.call()
        LibraryUtilsNativeMain.call()

        LibraryLinuxX64Main.call()
        LibraryUtilsLinuxX64Main.call()
    }
}