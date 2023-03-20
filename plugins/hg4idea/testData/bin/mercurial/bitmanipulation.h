#ifndef _HG_BITMANIPULATION_H_
#define _HG_BITMANIPULATION_H_

#include <string.h>

#include "compat.h"

/* Reads a 64 bit integer from big-endian bytes. Assumes that the data is long
 enough */
static inline uint64_t getbe64(const char *c)
{
	const unsigned char *d = (const unsigned char *)c;

	return ((((uint64_t)d[0]) << 56) | (((uint64_t)d[1]) << 48) |
	        (((uint64_t)d[2]) << 40) | (((uint64_t)d[3]) << 32) |
	        (((uint64_t)d[4]) << 24) | (((uint64_t)d[5]) << 16) |
	        (((uint64_t)d[6]) << 8) | (d[7]));
}

static inline uint32_t getbe32(const char *c)
{
	const unsigned char *d = (const unsigned char *)c;

	return ((((uint32_t)d[0]) << 24) | (((uint32_t)d[1]) << 16) |
	        (((uint32_t)d[2]) << 8) | (d[3]));
}

static inline int16_t getbeint16(const char *c)
{
	const unsigned char *d = (const unsigned char *)c;

	return ((d[0] << 8) | (d[1]));
}

static inline uint16_t getbeuint16(const char *c)
{
	const unsigned char *d = (const unsigned char *)c;

	return ((d[0] << 8) | (d[1]));
}

/* Writes a 64 bit integer to bytes in a big-endian format.
 Assumes that the buffer is long enough */
static inline void putbe64(uint64_t x, char *c)
{
	c[0] = (x >> 56) & 0xff;
	c[1] = (x >> 48) & 0xff;
	c[2] = (x >> 40) & 0xff;
	c[3] = (x >> 32) & 0xff;
	c[4] = (x >> 24) & 0xff;
	c[5] = (x >> 16) & 0xff;
	c[6] = (x >> 8) & 0xff;
	c[7] = (x)&0xff;
}

static inline void putbe32(uint32_t x, char *c)
{
	c[0] = (x >> 24) & 0xff;
	c[1] = (x >> 16) & 0xff;
	c[2] = (x >> 8) & 0xff;
	c[3] = (x)&0xff;
}

static inline double getbefloat64(const char *c)
{
	const unsigned char *d = (const unsigned char *)c;
	double ret;
	int i;
	uint64_t t = 0;
	for (i = 0; i < 8; i++) {
		t = (t << 8) + d[i];
	}
	memcpy(&ret, &t, sizeof(t));
	return ret;
}

#endif
