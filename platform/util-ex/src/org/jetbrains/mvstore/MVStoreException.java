/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.jetbrains.mvstore;

/**
 * Various kinds of MVStore problems, along with associated error code.
 */
public final class MVStoreException extends RuntimeException {
    /**
     * The file is locked.
     */
    public static final int ERROR_FILE_LOCKED = 7;

    /**
     * An error occurred when trying to write to the file.
     */
    public static final int ERROR_WRITING_FAILED = 2;

    /**
     * The file format is not supported.
     */
    public static final int ERROR_UNSUPPORTED_FORMAT = 5;

    /**
     * The file is corrupt or (for encrypted files) the encryption key is wrong.
     */
    public static final int ERROR_FILE_CORRUPT = 6;

    /**
     * An error occurred while reading from the file.
     */
    public static final int ERROR_READING_FAILED = 1;

    /**
     * The object is already closed.
     */
    public static final int ERROR_CLOSED = 4;

    /**
     * An internal error occurred. This could be a bug, or a memory corruption
     * (for example caused by out of memory).
     */
    public static final int ERROR_INTERNAL = 3;

    /**
     * The application was trying to read data from a chunk that is no longer
     * available.
     */
    public static final int ERROR_CHUNK_NOT_FOUND = 9;

    private final int errorCode;

    public MVStoreException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public MVStoreException(int errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public MVStoreException(int errorCode, Throwable cause) {
        super(cause);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
