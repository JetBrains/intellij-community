#ifndef _HG_COMPAT_H_
#define _HG_COMPAT_H_

#ifdef _WIN32
#ifdef _MSC_VER
#if _MSC_VER < 1900
/* msvc 6.0 has problems */
#define inline __inline
#if defined(_WIN64)
typedef __int64 ssize_t;
typedef unsigned __int64 uintptr_t;
#else
typedef int ssize_t;
typedef unsigned int uintptr_t;
#endif
typedef signed char int8_t;
typedef short int16_t;
typedef long int32_t;
typedef __int64 int64_t;
typedef unsigned char uint8_t;
typedef unsigned short uint16_t;
typedef unsigned long uint32_t;
typedef unsigned __int64 uint64_t;
#else
/* VC++ 14 */
#include <stdint.h>

#if defined(_WIN64)
typedef __int64 ssize_t;
#else
typedef int ssize_t;
#endif
#endif /* _MSC_VER < 1900 */

#else
/* not msvc */
#include <stdint.h>
#endif
#else
/* not windows */
#include <sys/types.h>
#if defined __BEOS__ && !defined __HAIKU__
#include <ByteOrder.h>
#else
#include <arpa/inet.h>
#endif
#include <inttypes.h>
#endif

#if defined __hpux || defined __SUNPRO_C || defined _AIX
#define inline
#endif

#ifdef __linux
#define inline __inline
#endif

#endif
