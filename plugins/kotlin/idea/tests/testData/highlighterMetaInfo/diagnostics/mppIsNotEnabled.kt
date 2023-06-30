// KTIJ-24132
// ALLOW_ERRORS

public expect interface CompletionHandler {
    public operator fun invoke(cause: Throwable?)
}

public actual typealias CompletionHandler = (cause: Throwable?) -> Unit