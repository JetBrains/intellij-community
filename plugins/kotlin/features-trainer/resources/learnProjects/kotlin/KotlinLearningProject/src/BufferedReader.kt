import java.io.InputStream

/**
 * This is a sample class used in IDE Feature Trainer
 * for illustration properties
 */
class BufferedReader {
    private var inputStream: InputStream
    private var maxBufferSize = 1024

    constructor(inputStream: InputStream) {
        this.inputStream = inputStream
    }

    constructor(inputStream: InputStream, maxBufferSize: Int) {
        this.inputStream = inputStream
        this.maxBufferSize = maxBufferSize
    }

    /**
     * @return one byte from input stream
     */
    fun read(): Byte {
        throw IllegalStateException("Not implemented yet")
    }

    /**
     * @return n bytes from input stream
     */
    fun read(n: Int): ByteArray {
        throw IllegalStateException("Not implemented yet")
    }

    /**
     * @return one line from input stream as String
     */
    fun readLine(): String {
        throw IllegalStateException("Not implemented yet")
    }

    /**
     * @return all lines from input stream as List of Strings
     */
    fun lines(): List<String> {
        throw IllegalStateException("Not implemented yet")
    }
}