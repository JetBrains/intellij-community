object AppNativeMain {
    init {
        LibraryCommonMain.call()
        LibraryUtilsCommonMain.call()
        
        LibraryNativeMain.call()
        LibraryUtilsNativeMain.call()
    }
}