/*
 * Copyright (c) 2007 David Crawshaw <david@zentus.com>
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

#include <jni.h>
/* Header for class org_sqlite__NativeDB */

#ifndef _Included_org_jetbrains_sqlite_NativeDB
#define _Included_org_jetbrains_sqlite_NativeDB
#ifdef __cplusplus
extern "C" {
#endif
#undef org_jetbrains_sqlite_NativeDB_DEFAULT_BACKUP_BUSY_SLEEP_TIME_MILLIS
#define org_jetbrains_sqlite_NativeDB_DEFAULT_BACKUP_BUSY_SLEEP_TIME_MILLIS 100L
#undef org_jetbrains_sqlite_NativeDB_DEFAULT_BACKUP_NUM_BUSY_BEFORE_FAIL
#define org_jetbrains_sqlite_NativeDB_DEFAULT_BACKUP_NUM_BUSY_BEFORE_FAIL 3L
#undef org_jetbrains_sqlite_NativeDB_DEFAULT_PAGES_PER_BACKUP_STEP
#define org_jetbrains_sqlite_NativeDB_DEFAULT_PAGES_PER_BACKUP_STEP 100L
/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    open
 * Signature: ([BI)I
 */
JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_open
  (JNIEnv *, jobject, jbyteArray, jint);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    _close
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB__1close
  (JNIEnv *, jobject);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    _exec_utf8
 * Signature: ([B)I
 */
JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB__1exec_1utf8
  (JNIEnv *, jobject, jbyteArray);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    shared_cache
 * Signature: (Z)I
 */
JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_shared_1cache
  (JNIEnv *, jobject, jboolean);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    enable_load_extension
 * Signature: (Z)I
 */
JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_enable_1load_1extension
  (JNIEnv *, jobject, jboolean);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    interrupt
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_interrupt
  (JNIEnv *, jobject);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    busy_timeout
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_busy_1timeout
  (JNIEnv *, jobject, jint);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    busy_handler
 * Signature: (Lorg/sqlite/BusyHandler;)V
 */
JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_busy_1handler
  (JNIEnv *, jobject, jobject);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    prepare_utf8
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_org_jetbrains_sqlite_NativeDB_prepare_1utf8
  (JNIEnv *, jobject, jbyteArray);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    errmsg_utf8
 * Signature: ()Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL Java_org_jetbrains_sqlite_NativeDB_errmsg_1utf8
  (JNIEnv *, jobject);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    libversion_utf8
 * Signature: ()Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL Java_org_jetbrains_sqlite_NativeDB_libversion_1utf8
  (JNIEnv *, jobject);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    changes
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_org_jetbrains_sqlite_NativeDB_changes
  (JNIEnv *, jobject);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    total_changes
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_org_jetbrains_sqlite_NativeDB_total_1changes
  (JNIEnv *, jobject);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    finalize
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_finalize
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    step
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_step
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    reset
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_reset
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    clear_bindings
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_clear_1bindings
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    bind_parameter_count
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_bind_1parameter_1count
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    column_count
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_column_1count
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    column_type
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_column_1type
  (JNIEnv *, jobject, jlong, jint);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    column_text_utf8
 * Signature: (JI)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL Java_org_jetbrains_sqlite_NativeDB_column_1text_1utf8
  (JNIEnv *, jobject, jlong, jint);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    column_blob
 * Signature: (JI)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL Java_org_jetbrains_sqlite_NativeDB_column_1blob
  (JNIEnv *, jobject, jlong, jint);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    column_double
 * Signature: (JI)D
 */
JNIEXPORT jdouble JNICALL Java_org_jetbrains_sqlite_NativeDB_column_1double
  (JNIEnv *, jobject, jlong, jint);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    column_long
 * Signature: (JI)J
 */
JNIEXPORT jlong JNICALL Java_org_jetbrains_sqlite_NativeDB_column_1long
  (JNIEnv *, jobject, jlong, jint);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    column_int
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_column_1int
  (JNIEnv *, jobject, jlong, jint);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    bind_null
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_bind_1null
  (JNIEnv *, jobject, jlong, jint);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    bind_int
 * Signature: (JII)I
 */
JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_bind_1int
  (JNIEnv *, jobject, jlong, jint, jint);

JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_executeBatch(JNIEnv *, jobject, jlong, jint, jint, jintArray);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    bind_long
 * Signature: (JIJ)I
 */
JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_bind_1long
  (JNIEnv *, jobject, jlong, jint, jlong);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    bind_double
 * Signature: (JID)I
 */
JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_bind_1double
  (JNIEnv *, jobject, jlong, jint, jdouble);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    bind_text_utf8
 * Signature: (JI[B)I
 */
JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_bind_1text_1utf8
  (JNIEnv *, jobject, jlong, jint, jbyteArray);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    bind_blob
 * Signature: (JI[B)I
 */
JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_bind_1blob
  (JNIEnv *, jobject, jlong, jint, jbyteArray);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    result_null
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_result_1null
  (JNIEnv *, jobject, jlong);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    result_text_utf8
 * Signature: (J[B)V
 */
JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_result_1text_1utf8
  (JNIEnv *, jobject, jlong, jbyteArray);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    result_blob
 * Signature: (J[B)V
 */
JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_result_1blob
  (JNIEnv *, jobject, jlong, jbyteArray);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    result_double
 * Signature: (JD)V
 */
JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_result_1double
  (JNIEnv *, jobject, jlong, jdouble);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    result_long
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_result_1long
  (JNIEnv *, jobject, jlong, jlong);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    result_int
 * Signature: (JI)V
 */
JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_result_1int
  (JNIEnv *, jobject, jlong, jint);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    result_error_utf8
 * Signature: (J[B)V
 */
JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_result_1error_1utf8
  (JNIEnv *, jobject, jlong, jbyteArray);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    limit
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_limit
  (JNIEnv *, jobject, jint, jint);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    backup
 * Signature: ([B[BLorg/sqlite/core/DB/ProgressObserver;III)I
 */
JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_backup
  (JNIEnv *, jobject, jbyteArray, jbyteArray, jobject, jint, jint, jint);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    restore
 * Signature: ([B[BLorg/sqlite/core/DB/ProgressObserver;III)I
 */
JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_restore
  (JNIEnv *, jobject, jbyteArray, jbyteArray, jobject, jint, jint, jint);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    set_commit_listener
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_set_1commit_1listener
  (JNIEnv *, jobject, jboolean);

/*
 * Class:     org_jetbrains_sqlite_NativeDB
 * Method:    set_update_listener
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_set_1update_1listener
  (JNIEnv *, jobject, jboolean);

#ifdef __cplusplus
}
#endif
#endif
