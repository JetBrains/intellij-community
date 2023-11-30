// KTIJ-24132
// ALLOW_ERRORS
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY

public expect interface CompletionHandler {
    public operator fun invoke(cause: Throwable?)
}

public actual typealias CompletionHandler = (cause: Throwable?) -> Unit