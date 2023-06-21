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

#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include "NativeDB.h"
#include "sqlite3.h"

// Java class variables and method references initialized on library load.
// These classes are weak references to that if the classloader is no longer referenced (garbage)
// It can be garbage collected. The weak references are freed on unload.
// These should not be static variables within methods because assignment in C is not
// guaranteed to be atomic, meaning that one thread may have half initialized a reference
// while another thread reads this half reference resulting in a crash.

static jclass dbclass = 0;
static jfieldID dbpointer = 0;
static jmethodID mth_stringToUtf8ByteArray = 0;
static jmethodID mth_throwex = 0;
static jmethodID mth_throwexcode = 0;
static jmethodID mth_throwexmsg = 0;

static jclass pclass = 0;
static jmethodID pmethod = 0;

static jmethodID exp_msg = 0;

static void * toref(jlong value)
{
    void * ret;
    memcpy(&ret, &value, sizeof(void*));
    return ret;
}

static jlong fromref(void * value)
{
    jlong ret;
    memcpy(&ret, &value, sizeof(void*));
    return ret;
}

static void throwex(JNIEnv *env, jobject this)
{
    (*env)->CallVoidMethod(env, this, mth_throwex);
}

static void throwex_errorcode(JNIEnv *env, jobject this, int errorCode)
{
    (*env)->CallVoidMethod(env, this, mth_throwexcode, (jint) errorCode);
}

static void throwex_msg(JNIEnv *env, const char *str)
{
    (*env)->CallStaticVoidMethod(env, dbclass, mth_throwexmsg, (*env)->NewStringUTF(env, str));
}

static void throwex_outofmemory(JNIEnv *env)
{
    throwex_msg(env, "Out of memory");
}

static void throwex_stmt_finalized(JNIEnv *env)
{
    throwex_msg(env, "The prepared statement has been finalized");
}

static void throwex_db_closed(JNIEnv *env)
{
    throwex_msg(env, "The database has been closed");
}

static jobject utf8BytesToDirectByteBuffer(JNIEnv *env, const char* bytes, int nbytes)
{
    jobject result;

    if (!bytes)
    {
        return NULL;
    }

    result = (*env)->NewDirectByteBuffer(env, (void*) bytes, nbytes);
    if (!result)
    {
        throwex_outofmemory(env);
        return NULL;
    }

    return result;
}

static void utf8JavaByteArrayToUtf8Bytes(JNIEnv *env, jbyteArray utf8bytes, char** bytes, int* nbytes)
{
    jsize utf8bytes_length;
    char* buf;

    *bytes = NULL;
    if (nbytes) *nbytes = 0;

    if (!utf8bytes)
    {
        return;
    }

    utf8bytes_length = (*env)->GetArrayLength(env, (jarray) utf8bytes);

    buf = (char*) malloc(utf8bytes_length + 1);
    if (!buf)
    {
        throwex_outofmemory(env);
        return;
    }

    (*env)->GetByteArrayRegion(env, utf8bytes, 0, utf8bytes_length, (jbyte*)buf);

    buf[utf8bytes_length] = '\0';

    *bytes = buf;
    if (nbytes) *nbytes = (int) utf8bytes_length;
}

static jbyteArray stringToUtf8ByteArray(JNIEnv *env, jstring str)
{
    jobject result;

    result = (*env)->CallStaticObjectMethod(env, dbclass, mth_stringToUtf8ByteArray, str);

    return (jbyteArray) result;
}

static void stringToUtf8Bytes(JNIEnv *env, jstring str, char** bytes, int* nbytes)
{
    jbyteArray utf8bytes;
    jsize utf8bytes_length;
    char* buf;

    *bytes = NULL;
    if (nbytes) *nbytes = 0;

    if (!str)
    {
        return;
    }

    utf8bytes = stringToUtf8ByteArray(env, str);
    if (!utf8bytes)
     {
        return;
    }

    utf8bytes_length = (*env)->GetArrayLength(env, (jarray) utf8bytes);

    buf = (char*) malloc(utf8bytes_length + 1);
    if (!buf)
    {
        throwex_outofmemory(env);
        return;
    }

    (*env)->GetByteArrayRegion(env, utf8bytes, 0, utf8bytes_length, (jbyte*)buf);

    buf[utf8bytes_length] = '\0';

    *bytes = buf;
    if (nbytes) *nbytes = (int) utf8bytes_length;
}

static void freeUtf8Bytes(char* bytes)
{
    if (bytes)
    {
        free(bytes);
    }
}

static sqlite3 * gethandle(JNIEnv *env, jobject nativeDB)
{
    return (sqlite3 *)toref((*env)->GetLongField(env, nativeDB, dbpointer));
}

static void sethandle(JNIEnv *env, jobject nativeDB, sqlite3 * ref)
{
    (*env)->SetLongField(env, nativeDB, dbpointer, fromref(ref));
}

struct BusyHandlerContext {
    JavaVM * vm;
    jmethodID methodId;
    jobject obj;
};

static void free_busy_handler(JNIEnv *env, void *toFree) {
    struct BusyHandlerContext* busyHandlerContext = (struct BusyHandlerContext*) toFree;
    (*env)->DeleteGlobalRef(env, busyHandlerContext->obj);
    free(toFree);
}

static void set_new_handler(JNIEnv *env, jobject nativeDB, char *fieldName,
                             void * newHandler, void (*free_handler)(JNIEnv*, void*))
{
    jfieldID handlerField = (*env)->GetFieldID(env, dbclass, fieldName, "J");
    assert(handlerField);

    void *toFree = toref((*env)->GetLongField(env, nativeDB, handlerField));
    if (toFree) {
        free_handler(env, toFree);
    }

    (*env)->SetLongField(env, nativeDB, handlerField, fromref(newHandler));
}

// INITIALISATION ///////////////////////////////////////////////////

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
    JNIEnv* env = 0;

    if (JNI_OK != (*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_2)) {
						return JNI_ERR;
				}

    dbclass = (*env)->FindClass(env, "org/jetbrains/sqlite/NativeDB");
    if (!dbclass) {
    		return JNI_ERR;
    }

    dbclass = (*env)->NewWeakGlobalRef(env, dbclass);
    dbpointer = (*env)->GetFieldID(env, dbclass, "pointer", "J");
    mth_stringToUtf8ByteArray = (*env)->GetStaticMethodID(
            env, dbclass, "stringToUtf8ByteArray", "(Ljava/lang/String;)[B");
    mth_throwex = (*env)->GetMethodID(env, dbclass, "throwex", "()V");
    mth_throwexcode = (*env)->GetMethodID(env, dbclass, "throwex", "(I)V");
    mth_throwexmsg = (*env)->GetStaticMethodID(env, dbclass, "throwex", "(Ljava/lang/String;)V");

    pclass = (*env)->FindClass(env, "org/jetbrains/sqlite/SqliteDb$ProgressObserver");
    if(!pclass) return JNI_ERR;
    pclass = (*env)->NewWeakGlobalRef(env, pclass);
    pmethod = (*env)->GetMethodID(env, pclass, "progress", "(II)V");

//    phandleclass = (*env)->FindClass(env, "org/jetbrains/sqlite/ProgressHandler");
//    if(!phandleclass) return JNI_ERR;
//    phandleclass = (*env)->NewWeakGlobalRef(env, phandleclass);

    jclass exclass = (*env)->FindClass(env, "java/lang/Throwable");
    exp_msg = (*env)->GetMethodID(
            env, exclass, "toString", "()Ljava/lang/String;");

    return JNI_VERSION_10;
}

// FINALIZATION

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv* env = 0;

    if (JNI_OK != (*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_2))
        return;

    if (dbclass) (*env)->DeleteWeakGlobalRef(env, dbclass);

    if (pclass) (*env)->DeleteWeakGlobalRef(env, pclass);

//    if (phandleclass) (*env)->DeleteWeakGlobalRef(env, phandleclass);
}


// WRAPPERS for sqlite_* functions //////////////////////////////////

//JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_shared_1cache(
//        JNIEnv *env, jobject this, jboolean enable)
//{
//    return sqlite3_enable_shared_cache(enable ? 1 : 0);
//}


JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_enable_1load_1extension(
        JNIEnv *env, jobject this, jboolean enable)
{
    sqlite3 *db = gethandle(env, this);
    if (!db)
    {
        throwex_db_closed(env);
        return SQLITE_MISUSE;
    }

    return sqlite3_enable_load_extension(db, enable ? 1 : 0);
}


JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_open(JNIEnv *env, jobject this, jbyteArray file, jint flags) {
  int ret;
  sqlite3 *db;
  char *file_bytes;

  // compiled with SQLITE_OMIT_AUTOINIT
  ret = sqlite3_initialize();
  if (ret != SQLITE_OK) {
    return ret;
  }

  utf8JavaByteArrayToUtf8Bytes(env, file, &file_bytes, NULL);
  if (!file_bytes) {
    return SQLITE_ERROR;
  }

  ret = sqlite3_open_v2(file_bytes, &db, flags, NULL);
  freeUtf8Bytes(file_bytes);

  if (ret != SQLITE_OK) {
    ret = sqlite3_extended_errcode(db);
    sqlite3_close(db);
    return ret;
  }

  sethandle(env, this, db);

  // ignore failures, as we can tolerate regular result codes
  (void) sqlite3_extended_result_codes(db, 1);
  return SQLITE_OK;
}

JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_interrupt(JNIEnv *env, jobject this)
{
    sqlite3 *db = gethandle(env, this);
    if (!db)
    {
        throwex_db_closed(env);
        return;
    }

    sqlite3_interrupt(db);
}

JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_busy_1timeout(
    JNIEnv *env, jobject this, jint ms)
{
    sqlite3 *db = gethandle(env, this);
    if (!db)
    {
        throwex_db_closed(env);
        return;
    }

    sqlite3_busy_timeout(db, ms);
}

int busyHandlerCallBack(void* callback, int nbPrevInvok) {
    JNIEnv *env = 0;

    struct BusyHandlerContext *busyHandlerContext = (struct BusyHandlerContext*) callback;
    (*(busyHandlerContext->vm))->AttachCurrentThread(busyHandlerContext->vm, (void **)&env, 0);

    return (*env)->CallIntMethod(   env, 
                                    busyHandlerContext->obj,
                                    busyHandlerContext->methodId,
                                    nbPrevInvok);
}

void change_busy_handler(JNIEnv *env, jobject nativeDB, jobject busyHandler)
{
    sqlite3 *db;

    db = gethandle(env, nativeDB);
    if (!db){
        throwex_db_closed(env);
        return;
    }

    struct BusyHandlerContext* busyHandlerContext = NULL;

    if (busyHandler) {
        busyHandlerContext = (struct BusyHandlerContext*) malloc(sizeof(struct BusyHandlerContext));
        (*env)->GetJavaVM(env, &busyHandlerContext->vm);

        busyHandlerContext->obj = (*env)->NewGlobalRef(env, busyHandler);
        busyHandlerContext->methodId = (*env)->GetMethodID(  env,
                                                   (*env)->GetObjectClass(env, busyHandlerContext->obj),
                                                   "callback",
                                                   "(I)I");
    }

    if (busyHandlerContext) {
        sqlite3_busy_handler(db, &busyHandlerCallBack, busyHandlerContext);
    } else {
        sqlite3_busy_handler(db, NULL, NULL);
    }

    set_new_handler(env, nativeDB, "busyHandler", busyHandlerContext, &free_busy_handler);
}

JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_busy_1handler(
    JNIEnv *env, jobject nativeDB, jobject busyHandler) {
    change_busy_handler(env, nativeDB, busyHandler);
}

JNIEXPORT jlong JNICALL Java_org_jetbrains_sqlite_NativeDB_prepare_1utf8(
        JNIEnv *env, jobject this, jbyteArray sql)
{
    sqlite3* db;
    sqlite3_stmt* stmt;
    char* sql_bytes;
    int sql_nbytes;
    int status;

    db = gethandle(env, this);
    if (!db)
    {
        throwex_db_closed(env);
        return 0;
    }

    utf8JavaByteArrayToUtf8Bytes(env, sql, &sql_bytes, &sql_nbytes);
    if (!sql_bytes) return fromref(0);

    status = sqlite3_prepare_v2(db, sql_bytes, sql_nbytes, &stmt, 0);
    freeUtf8Bytes(sql_bytes);

    if (status != SQLITE_OK) {
        throwex_errorcode(env, this, status);
        return fromref(0);
    }
    return fromref(stmt);
}

JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB__1exec_1utf8(
        JNIEnv *env, jobject this, jbyteArray sql)
{
    sqlite3* db;
    char* sql_bytes;
    int status;

    db = gethandle(env, this);
    if (!db)
    {
        throwex_errorcode(env, this, SQLITE_MISUSE);
        return SQLITE_MISUSE;
    }

    utf8JavaByteArrayToUtf8Bytes(env, sql, &sql_bytes, NULL);
    if (!sql_bytes)
    {
        return SQLITE_ERROR;
    }

    status = sqlite3_exec(db, sql_bytes, 0, 0, NULL);
    freeUtf8Bytes(sql_bytes);

    if (status != SQLITE_OK) {
        throwex_errorcode(env, this, status);
    }

    return status;
}


JNIEXPORT jobject JNICALL Java_org_jetbrains_sqlite_NativeDB_errmsg_1utf8(JNIEnv *env, jobject this)
{
    sqlite3 *db;
    const char *str;

    db = gethandle(env, this);
    if (!db)
    {
        throwex_db_closed(env);
        return NULL;
    }
    
    str = (const char*) sqlite3_errmsg(db);
    if (!str) return NULL;
    return utf8BytesToDirectByteBuffer(env, str, strlen(str));
}

JNIEXPORT jobject JNICALL Java_org_jetbrains_sqlite_NativeDB_libversion_1utf8(
        JNIEnv *env, jobject this)
{
    const char* version = sqlite3_libversion();
    return utf8BytesToDirectByteBuffer(env, version, strlen(version));
}

JNIEXPORT jlong JNICALL Java_org_jetbrains_sqlite_NativeDB_changes(
        JNIEnv *env, jobject this)
{
    sqlite3 *db = gethandle(env, this);
    if (!db)
    {
        throwex_db_closed(env);
        return 0;
    }

    return sqlite3_changes64(db);
}

JNIEXPORT jlong JNICALL Java_org_jetbrains_sqlite_NativeDB_total_1changes(
        JNIEnv *env, jobject this)
{
    sqlite3 *db = gethandle(env, this);
    if (!db)
    {
        throwex_db_closed(env);
        return 0;
    }

    return sqlite3_total_changes64(db);
}

JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_finalize(
        JNIEnv *env, jobject this, jlong stmt)
{
    if (!stmt)
    {
        throwex_stmt_finalized(env);
        return SQLITE_MISUSE;
    }

    return sqlite3_finalize(toref(stmt));
}

JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_step(
        JNIEnv *env, jobject this, jlong stmt)
{
    if (!stmt)
    {
        throwex_stmt_finalized(env);
        return SQLITE_MISUSE;
    }

    return sqlite3_step(toref(stmt));
}

JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_reset(
        JNIEnv *env, jobject this, jlong stmt)
{
    if (!stmt)
    {
        throwex_stmt_finalized(env);
        return SQLITE_MISUSE;
    }

    return sqlite3_reset(toref(stmt));
}

JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_clear_1bindings(
        JNIEnv *env, jobject this, jlong stmt)
{
    if (!stmt)
    {
        throwex_stmt_finalized(env);
        return SQLITE_MISUSE;
    }

    return sqlite3_clear_bindings(toref(stmt));
}

JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_bind_1parameter_1count(
        JNIEnv *env, jobject this, jlong stmt)
{
    if (!stmt)
    {
        throwex_stmt_finalized(env);
        return SQLITE_MISUSE;
    }

    return sqlite3_bind_parameter_count(toref(stmt));
}

JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_column_1count(
        JNIEnv *env, jobject this, jlong stmt)
{
    if (!stmt)
    {
        throwex_stmt_finalized(env);
        return SQLITE_MISUSE;
    }

    return sqlite3_column_count(toref(stmt));
}

JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_column_1type(
        JNIEnv *env, jobject this, jlong stmt, jint col)
{
    if (!stmt)
    {
        throwex_stmt_finalized(env);
        return SQLITE_MISUSE;
    }

    return sqlite3_column_type(toref(stmt), col);
}

//JNIEXPORT jobject JNICALL Java_org_jetbrains_sqlite_NativeDB_column_1table_1name_1utf8(
//        JNIEnv *env, jobject this, jlong stmt, jint col)
//{
//    const char *str;
//
//    if (!stmt)
//    {
//        throwex_stmt_finalized(env);
//        return NULL;
//    }
//
//    str = sqlite3_column_table_name(toref(stmt), col);
//    if (!str) return NULL;
//    return utf8BytesToDirectByteBuffer(env, str, strlen(str));
//}

//JNIEXPORT jobject JNICALL Java_org_jetbrains_sqlite_NativeDB_column_1name_1utf8(
//        JNIEnv *env, jobject this, jlong stmt, jint col)
//{
//    const char *str;
//
//    if (!stmt)
//    {
//        throwex_stmt_finalized(env);
//        return NULL;
//    }
//
//    str = sqlite3_column_name(toref(stmt), col);
//    if (!str) return NULL;
//
//    return utf8BytesToDirectByteBuffer(env, str, strlen(str));
//}

JNIEXPORT jobject JNICALL Java_org_jetbrains_sqlite_NativeDB_column_1text_1utf8(
        JNIEnv *env, jobject this, jlong stmt, jint col)
{
    sqlite3 *db;
    const char *bytes;
    int nbytes;

    db = gethandle(env, this);
    if (!db)
    {
        throwex_db_closed(env);
        return NULL;
    }

    if (!stmt)
    {
        throwex_stmt_finalized(env);
        return NULL;
    }

    bytes = (const char*) sqlite3_column_text(toref(stmt), col);
    nbytes = sqlite3_column_bytes(toref(stmt), col);

    if (!bytes && sqlite3_errcode(db) == SQLITE_NOMEM)
    {
        throwex_outofmemory(env);
        return NULL;
    }

    return utf8BytesToDirectByteBuffer(env, bytes, nbytes);
}

JNIEXPORT jbyteArray JNICALL Java_org_jetbrains_sqlite_NativeDB_column_1blob(
        JNIEnv *env, jobject this, jlong stmt, jint col)
{
    sqlite3 *db;
    int type;
    int length;
    jbyteArray jBlob;
    const void *blob;

    db = gethandle(env, this);
    if (!db)
    {
        throwex_db_closed(env);
        return NULL;
    }

    if (!stmt)
    {
        throwex_stmt_finalized(env);
        return NULL;
    }

    // The value returned by sqlite3_column_type() is only meaningful if no type conversions have occurred
    type = sqlite3_column_type(toref(stmt), col);
    blob = sqlite3_column_blob(toref(stmt), col);
    if (!blob && sqlite3_errcode(db) == SQLITE_NOMEM)
    {
        throwex_outofmemory(env);
        return NULL;
    }
    if (!blob) {
        if (type == SQLITE_NULL) {
            return NULL;
        }
        else {
            // The return value from sqlite3_column_blob() for a zero-length BLOB is a NULL pointer.
            jBlob = (*env)->NewByteArray(env, 0);
            if (!jBlob) { throwex_outofmemory(env); return NULL; }
            return jBlob;
        }
    }

    length = sqlite3_column_bytes(toref(stmt), col);
    jBlob = (*env)->NewByteArray(env, length);
    if (!jBlob) { throwex_outofmemory(env); return NULL; }

    (*env)->SetByteArrayRegion(env, jBlob, (jsize) 0, (jsize) length, (const jbyte*) blob);

    return jBlob;
}

JNIEXPORT jdouble JNICALL Java_org_jetbrains_sqlite_NativeDB_column_1double(
        JNIEnv *env, jobject this, jlong stmt, jint col)
{
    if (!stmt)
    {
        throwex_stmt_finalized(env);
        return 0;
    }

    return sqlite3_column_double(toref(stmt), col);
}

JNIEXPORT jlong JNICALL Java_org_jetbrains_sqlite_NativeDB_column_1long(
        JNIEnv *env, jobject this, jlong stmt, jint col)
{
    if (!stmt)
    {
        throwex_stmt_finalized(env);
        return 0;
    }

    return sqlite3_column_int64(toref(stmt), col);
}

JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_column_1int(
        JNIEnv *env, jobject this, jlong stmt, jint col)
{
    if (!stmt)
    {
        throwex_stmt_finalized(env);
        return 0;
    }

    return sqlite3_column_int(toref(stmt), col);
}

JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_bind_1null(
        JNIEnv *env, jobject this, jlong stmt, jint pos)
{
    if (!stmt)
    {
        throwex_stmt_finalized(env);
        return SQLITE_MISUSE;
    }

    return sqlite3_bind_null(toref(stmt), pos);
}

JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_bind_1int(
        JNIEnv *env, jobject this, jlong stmt, jint pos, jint v)
{
    if (!stmt)
    {
        throwex_stmt_finalized(env);
        return SQLITE_MISUSE;
    }

    return sqlite3_bind_int(toref(stmt), pos, v);
}

JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_executeBatch
(
  JNIEnv *env, jobject this, jlong statement, jint queryCount, jint paramCount, jintArray data
)
{
  if (!statement)
  {
      throwex_stmt_finalized(env);
      return SQLITE_MISUSE;
  }

  int batchIndex;
  int position;
  int status;
  jint *body = (*env)->GetIntArrayElements(env, data, NULL);
  for (batchIndex = 0; batchIndex < queryCount; batchIndex++) {
    sqlite3_reset(toref(statement));

    for (position = 0; position < paramCount; position++) {
      status = sqlite3_bind_int(toref(statement), position + 1, body[(batchIndex * paramCount) + position]);
      if (status != SQLITE_OK) {
        (*env)->ReleaseIntArrayElements(env, data, body, JNI_ABORT);
        throwex(env, this);
        return SQLITE_MISUSE;
      }
    }

    status = sqlite3_step(toref(statement));
    if (status != SQLITE_DONE) {
      (*env)->ReleaseIntArrayElements(env, data, body, JNI_ABORT);
      sqlite3_reset(toref(statement));
      throwex(env, this);
      return SQLITE_MISUSE;
    }
  }
  (*env)->ReleaseIntArrayElements(env, data, body, JNI_ABORT);
  sqlite3_reset(toref(statement));
  sqlite3_clear_bindings(toref(statement));

  return SQLITE_OK;
}

JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_bind_1long(
        JNIEnv *env, jobject this, jlong stmt, jint pos, jlong v)
{
    if (!stmt)
    {
        throwex_stmt_finalized(env);
        return SQLITE_MISUSE;
    }

    return sqlite3_bind_int64(toref(stmt), pos, v);
}

JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_bind_1double(
        JNIEnv *env, jobject this, jlong stmt, jint pos, jdouble v)
{
    if (!stmt)
    {
        throwex_stmt_finalized(env);
        return SQLITE_MISUSE;
    }

    return sqlite3_bind_double(toref(stmt), pos, v);
}

JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_bind_1text_1utf8(
        JNIEnv *env, jobject this, jlong stmt, jint pos, jbyteArray v)
{
    int rc;
    char* v_bytes;
    int v_nbytes;

    if (!stmt)
    {
        throwex_stmt_finalized(env);
        return SQLITE_MISUSE;
    }

    utf8JavaByteArrayToUtf8Bytes(env, v, &v_bytes, &v_nbytes);
    if (!v_bytes) return SQLITE_ERROR;

    rc = sqlite3_bind_text(toref(stmt), pos, v_bytes, v_nbytes, SQLITE_TRANSIENT);
    freeUtf8Bytes(v_bytes);

    return rc;
}

JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_bind_1blob(
        JNIEnv *env, jobject this, jlong stmt, jint pos, jbyteArray v)
{
    jint rc;
    void *a;
    jsize size;

    if (!stmt)
    {
        throwex_stmt_finalized(env);
        return SQLITE_MISUSE;
    }

    size = (*env)->GetArrayLength(env, v);
    a = (*env)->GetPrimitiveArrayCritical(env, v, 0);
    if (!a) { throwex_outofmemory(env); return 0; }
    rc = sqlite3_bind_blob(toref(stmt), pos, a, size, SQLITE_TRANSIENT);
    (*env)->ReleasePrimitiveArrayCritical(env, v, a, JNI_ABORT);
    return rc;
}

JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_result_1null(
        JNIEnv *env, jobject this, jlong context)
{
    if (!context) return;
    sqlite3_result_null(toref(context));
}

JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_result_1text_1utf8(
        JNIEnv *env, jobject this, jlong context, jbyteArray value)
{
    char* value_bytes;
    int value_nbytes;

    if (!context) return;
    if (value == NULL) { sqlite3_result_null(toref(context)); return; }

    utf8JavaByteArrayToUtf8Bytes(env, value, &value_bytes, &value_nbytes);
    if (!value_bytes)
    {
        sqlite3_result_error_nomem(toref(context));
        return;
    }

    sqlite3_result_text(toref(context), value_bytes, value_nbytes, SQLITE_TRANSIENT);
    freeUtf8Bytes(value_bytes);
}

JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_result_1blob(
        JNIEnv *env, jobject this, jlong context, jobject value)
{
    jbyte *bytes;
    jsize size;

    if (!context) return;
    if (value == NULL) { sqlite3_result_null(toref(context)); return; }

    size = (*env)->GetArrayLength(env, value);
    bytes = (*env)->GetPrimitiveArrayCritical(env, value, 0);
    if (!bytes) { throwex_outofmemory(env); return; }
    sqlite3_result_blob(toref(context), bytes, size, SQLITE_TRANSIENT);
    (*env)->ReleasePrimitiveArrayCritical(env, value, bytes, JNI_ABORT);
}

JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_result_1double(
        JNIEnv *env, jobject this, jlong context, jdouble value)
{
    if (!context) return;
    sqlite3_result_double(toref(context), value);
}

JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_result_1long(
        JNIEnv *env, jobject this, jlong context, jlong value)
{
    if (!context) return;
    sqlite3_result_int64(toref(context), value);
}

JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_result_1int(
        JNIEnv *env, jobject this, jlong context, jint value)
{
    if (!context) return;
    sqlite3_result_int(toref(context), value);
}

JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_result_1error_1utf8(
        JNIEnv *env, jobject this, jlong context, jbyteArray err)
{
    char* err_bytes;
    int err_nbytes;

    if (!context) return;

    utf8JavaByteArrayToUtf8Bytes(env, err, &err_bytes, &err_nbytes);
    if (!err_bytes)
    {
        sqlite3_result_error_nomem(toref(context));
        return;
    }

    sqlite3_result_error(toref(context), err_bytes, err_nbytes);
    freeUtf8Bytes(err_bytes);
}

JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_limit(JNIEnv *env, jobject this, jint id, jint value)
{
    sqlite3* db;

    db = gethandle(env, this);

    if (!db)
    {
        throwex_db_closed(env);
        return 0;
    }

    return sqlite3_limit(db, id, value);
}

// COMPOUND FUNCTIONS ///////////////////////////////////////////////
// backup function

void reportProgress(JNIEnv* env, jobject func, int remaining, int pageCount) {
  if(!func)
    return;

  (*env)->CallVoidMethod(env, func, pmethod, remaining, pageCount);
}

jmethodID getBackupRestoreMethod(JNIEnv *env, jobject progress) {
    if(!progress)
        return 0;

    jmethodID ret = (*env)->GetMethodID(env,
                                       (*env)->GetObjectClass(env, progress),
                                       "progress",
                                        "(II)V");
    return ret;
}

void updateProgress(JNIEnv *env, sqlite3_backup *pBackup, jobject progress, jmethodID progressMth) {
    if (progressMth) {
       int remaining = sqlite3_backup_remaining(pBackup);
       int pagecount = sqlite3_backup_pagecount(pBackup);
       (*env)->CallVoidMethod(env, progress, progressMth, remaining, pagecount);
    }
}

void copyLoop(JNIEnv *env, sqlite3_backup *pBackup, jobject progress,
              int pagesPerStep, int nTimeoutLimit, int sleepTimeMillis) {
    int rc;
    int nTimeout = 0;

    jmethodID progressMth = getBackupRestoreMethod(env, progress);

    do {
          rc = sqlite3_backup_step(pBackup, pagesPerStep);

          // if the step completed successfully, update progress
          if (rc == SQLITE_OK || rc == SQLITE_DONE) {
              updateProgress(env, pBackup, progress, progressMth);
          }

          if (rc == SQLITE_BUSY || rc == SQLITE_LOCKED) {
              if (nTimeout++ >= nTimeoutLimit)
                 break;
              sqlite3_sleep(sleepTimeMillis);
          }
    } while (rc == SQLITE_OK || rc == SQLITE_BUSY || rc == SQLITE_LOCKED);
}


/*
** Perform an online backup of database pDb to the database file named
** by zFilename. This function copies pagesPerStep database pages from pDb to
** zFilename per step. If the backup step returns SQLITE_BUSY or SQLITE_LOCKED,
** the function waits nTimeoutLimit milliseconds before trying again. Should
** this occur more than nTimeoutLimit times, the backup/restore will fail and
** the corresponding error code is returned. If any other return code is
** returned during the copy, the backup/restore is aborted, and error is
** returned.
**
** The third argument passed to this function must be a pointer to a progress
** function or null. After each set of pages is backed up, the progress function
** is invoked with two integer parameters: the number of pages left to
** copy, and the total number of pages in the source file. This information
** may be used, for example, to update a GUI progress bar.
**
** While this function is running, another thread may use the database pDb, or
** another process may access the underlying database file via a separate
** connection.
**
** If the backup process is successfully completed, SQLITE_OK is returned.
** Otherwise, if an error occurs, an SQLite error code is returned.
*/
JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_backup(
  JNIEnv *env, jobject this, 
  jbyteArray zDBName,
  jbyteArray zFilename,       /* Name of file to back up to */
  jobject observer,           /* Progress function to invoke */
  jint sleepTimeMillis,        /* number of milliseconds to sleep if DB is busy */
  jint nTimeoutLimit,          /* max number of SQLite Busy return codes before failing */
  jint pagesPerStep            /* number of DB pages to copy per step */
)
{
int rc;                     /* Function return code */
sqlite3* pDb;               /* Database to back up */
sqlite3* pFile;             /* Database connection opened on zFilename */
sqlite3_backup *pBackup;    /* Backup handle used to copy data */
char *dFileName;
char *dDBName;

pDb = gethandle(env, this);
if (!pDb)
{
  throwex_db_closed(env);
  return SQLITE_MISUSE;
}

utf8JavaByteArrayToUtf8Bytes(env, zFilename, &dFileName, NULL);
if (!dFileName)
{
  return SQLITE_NOMEM;
}

utf8JavaByteArrayToUtf8Bytes(env, zDBName, &dDBName, NULL);
if (!dDBName)
{
  freeUtf8Bytes(dFileName);
  return SQLITE_NOMEM;
}

/* Open the database file identified by dFileName. */
int flags = SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE;
if (sqlite3_strnicmp(dFileName, "file:", 5) == 0) {
  flags |= SQLITE_OPEN_URI;
}
rc = sqlite3_open_v2(dFileName, &pFile, flags, NULL);

if(rc == SQLITE_OK) {

  /* Open the sqlite3_backup object used to accomplish the transfer */
  pBackup = sqlite3_backup_init(pFile, "main", pDb, dDBName);
  if( pBackup ){
    copyLoop(env, pBackup, observer, pagesPerStep, nTimeoutLimit, sleepTimeMillis);

    /* Release resources allocated by backup_init(). */
    (void)sqlite3_backup_finish(pBackup);
  }
  rc = sqlite3_errcode(pFile);
}

/* Close the database connection opened on database file zFilename
** and return the result of this function. */
(void)sqlite3_close(pFile);

freeUtf8Bytes(dDBName);
freeUtf8Bytes(dFileName);

return rc;
}

JNIEXPORT jint JNICALL Java_org_jetbrains_sqlite_NativeDB_restore(
  JNIEnv *env, jobject this, 
  jbyteArray zDBName,
  jbyteArray zFilename,         /* Name of file to restore from */
  jobject observer,             /* Progress function to invoke */
  jint sleepTimeMillis,         /* number of milliseconds to sleep if DB is busy */
  jint nTimeoutLimit,           /* max number of SQLite Busy return codes before failing */
  jint pagesPerStep             /* number of DB pages to copy per step */
)
{
int rc;                     /* Function return code */
sqlite3* pDb;               /* Database to back up */
sqlite3* pFile;             /* Database connection opened on zFilename */
sqlite3_backup *pBackup;    /* Backup handle used to copy data */
char *dFileName;
char *dDBName;
int nTimeout = 0;

pDb = gethandle(env, this);
if (!pDb)
{
  throwex_db_closed(env);
  return SQLITE_MISUSE;
}

utf8JavaByteArrayToUtf8Bytes(env, zFilename, &dFileName, NULL);
if (!dFileName)
{
  return SQLITE_NOMEM;
}

utf8JavaByteArrayToUtf8Bytes(env, zDBName, &dDBName, NULL);
if (!dDBName)
{
  freeUtf8Bytes(dFileName);
  return SQLITE_NOMEM;
}

/* Open the database file identified by dFileName. */
int flags = SQLITE_OPEN_READONLY;
if (sqlite3_strnicmp(dFileName, "file:", 5) == 0) {
  flags |= SQLITE_OPEN_URI;
}
rc = sqlite3_open_v2(dFileName, &pFile, flags, NULL);

if (rc == SQLITE_OK) {

  /* Open the sqlite3_backup object used to accomplish the transfer */
  pBackup = sqlite3_backup_init(pDb, dDBName, pFile, "main");
  if (pBackup) {
    copyLoop(env, pBackup, observer, pagesPerStep, nTimeoutLimit, sleepTimeMillis);
    /* Release resources allocated by backup_init(). */
    (void)sqlite3_backup_finish(pBackup);
  }
  rc = sqlite3_errcode(pFile);
}

/* Close the database connection opened on database file zFilename
** and return the result of this function. */
(void)sqlite3_close(pFile);

freeUtf8Bytes(dDBName);
freeUtf8Bytes(dFileName);

return rc;
}

// Update hook

struct UpdateHandlerContext {
    JavaVM *vm;
    jobject handler;
    jmethodID method;
};


void update_hook(void *context, int type, char const *database, char const *table, sqlite3_int64 row) {
    JNIEnv *env = 0;
    struct UpdateHandlerContext* update_handler_context = (struct UpdateHandlerContext*) context;
    (*update_handler_context->vm)->AttachCurrentThread(update_handler_context->vm, (void **)&env, 0);

    jstring databaseString = (*env)->NewStringUTF(env, database);
    jstring tableString    = (*env)->NewStringUTF(env, table);

    (*env)->CallVoidMethod(env, update_handler_context->handler, update_handler_context->method, type, databaseString, tableString, row);

    (*env)->DeleteLocalRef(env, databaseString);
    (*env)->DeleteLocalRef(env, tableString);
}

static void free_update_handler(JNIEnv *env, void *ctx) {
    struct UpdateHandlerContext* update_handler_context = (struct UpdateHandlerContext*) ctx;
    (*env)->DeleteGlobalRef(env, update_handler_context->handler);
    free(ctx);
}

static void clear_update_listener(JNIEnv *env, jobject nativeDB){
    sqlite3_update_hook(gethandle(env, nativeDB), NULL, NULL);
    set_new_handler(env, nativeDB, "updateListener", NULL, &free_update_handler);
}

JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_set_1update_1listener(JNIEnv *env, jobject nativeDB, jboolean enabled) {
    if (enabled) {
        struct UpdateHandlerContext* update_handler_context = (struct UpdateHandlerContext*) malloc(sizeof(struct UpdateHandlerContext));
        update_handler_context->method = (*env)->GetMethodID(env, dbclass, "onUpdate", "(ILjava/lang/String;Ljava/lang/String;J)V");
        update_handler_context->handler = (*env)->NewGlobalRef(env, nativeDB);
        (*env)->GetJavaVM(env, &update_handler_context->vm);
        sqlite3_update_hook(gethandle(env, nativeDB), &update_hook, update_handler_context);
        set_new_handler(env, nativeDB, "updateListener", update_handler_context, &free_update_handler);
    } else {
        clear_update_listener(env, nativeDB);
    }
}

// Commit hook

struct CommitHandlerContext {
    JavaVM *vm;
    jobject handler;
    jmethodID method;
};

int commit_hook(void *context) {
    struct CommitHandlerContext *commit_handler_context = (struct CommitHandlerContext*) context;
    JNIEnv *env = 0;
    (*commit_handler_context->vm)->AttachCurrentThread(commit_handler_context->vm, (void **)&env, 0);
    (*env)->CallVoidMethod(env, commit_handler_context->handler, commit_handler_context->method, 1);
    return 0;
}

void rollback_hook(void *context) {
    struct CommitHandlerContext *commit_handler_context = (struct CommitHandlerContext*) context;
    JNIEnv *env = 0;
    (*commit_handler_context->vm)->AttachCurrentThread(commit_handler_context->vm, (void **)&env, 0);
    (*env)->CallVoidMethod(env, commit_handler_context->handler, commit_handler_context->method, 0);
}

static void freeCommitHandlerCtx(JNIEnv *env, void *ctx) {
    struct CommitHandlerContext* commit_handler_context = (struct CommitHandlerContext*) ctx;
    (*env)->DeleteGlobalRef(env, commit_handler_context->handler);
    free(ctx);
}

void clear_commit_listener(JNIEnv *env, jobject nativeDB, sqlite3 *db) {
    sqlite3_commit_hook(db, NULL, NULL);
    sqlite3_rollback_hook(db, NULL, NULL);
    set_new_handler(env, nativeDB, "commitListener", NULL, freeCommitHandlerCtx);
}

JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB_set_1commit_1listener(JNIEnv *env, jobject nativeDB, jboolean enabled) {
    sqlite3 *db = gethandle(env, nativeDB);
    if (enabled) {
        struct CommitHandlerContext *commit_handler_context = (struct CommitHandlerContext*) malloc(sizeof(struct CommitHandlerContext));
        commit_handler_context->handler = (*env)->NewGlobalRef(env, nativeDB);
        commit_handler_context->method  = (*env)->GetMethodID(env, dbclass, "onCommit", "(Z)V");
        (*env)->GetJavaVM(env, &commit_handler_context->vm);
        sqlite3_commit_hook(db, &commit_hook, commit_handler_context);
        sqlite3_rollback_hook(db, &rollback_hook, commit_handler_context);
        set_new_handler(env, nativeDB, "commitListener", commit_handler_context, freeCommitHandlerCtx);
    } else {
        clear_commit_listener(env, nativeDB, db);
    }
}

JNIEXPORT void JNICALL Java_org_jetbrains_sqlite_NativeDB__1close(
        JNIEnv *env, jobject nativeDB)
{
    sqlite3 *db = gethandle(env, nativeDB);
    if (db)
    {
//        change_progress_handler(env, nativeDB, NULL, 0);
        change_busy_handler(env, nativeDB, NULL);
        clear_commit_listener(env, nativeDB, db);
        clear_update_listener(env, nativeDB);

        if (sqlite3_close(db) != SQLITE_OK)
        {
            throwex(env, nativeDB);
        }
        sethandle(env, nativeDB, 0);
    }
}
