# sha1collisiondetection
Library and command line tool to detect SHA-1 collisions in files

Copyright 2017 Marc Stevens <marc@marc-stevens.nl>

Distributed under the MIT Software License.

See accompanying file LICENSE.txt or copy at https://opensource.org/licenses/MIT.

## Developers

- Marc Stevens, CWI Amsterdam (https://marc-stevens.nl)
- Dan Shumow, Microsoft Research (https://www.microsoft.com/en-us/research/people/danshu/)

## About
This library and command line tool were designed as near drop-in replacements for common SHA-1 libraries and sha1sum.
They will compute the SHA-1 hash of any given file and additionally will detect cryptanalytic collision attacks against SHA-1 present in each file. It is very fast and takes less than twice the amount of time as regular SHA-1.

More specifically they will detect any cryptanalytic collision attack against SHA-1 using any of the top 32 SHA-1 disturbance vectors with probability 1:
```
    I(43,0), I(44,0), I(45,0), I(46,0), I(47,0), I(48,0), I(49,0), I(50,0), I(51,0), I(52,0),
    I(46,2), I(47,2), I(48,2), I(49,2), I(50,2), I(51,2),
    II(45,0), II(46,0), II(47,0), II(48,0), II(49,0), II(50,0), II(51,0), II(52,0), II(53,0), II(54,0), II(55,0), II(56,0),
    II(46,2), II(49,2), II(50,2), II(51,2)
```
The possibility of false positives can be neglected as the probability is smaller than 2^-90.

The library supports both an indicator flag that applications can check and act on, as well as a special _safe-hash_ mode that returns the real SHA-1 hash when no collision was detected and a different _safe_ hash when a collision was detected.
Colliding files will have the same SHA-1 hash, but will have different unpredictable safe-hashes.
This essentially enables protection of applications against SHA-1 collisions with no further changes in the application, e.g., digital signature forgeries based on SHA-1 collisions automatically become invalid.

For the theoretical explanation of collision detection see the award-winning paper on _Counter-Cryptanalysis_:

Counter-cryptanalysis, Marc Stevens, CRYPTO 2013, Lecture Notes in Computer Science, vol. 8042, Springer, 2013, pp. 129-146,
https://marc-stevens.nl/research/papers/C13-S.pdf

## Compiling

Run:
```
make
```

## Command-line usage

There are two programs `bin/sha1dcsum` and `bin/sha1dcsum_partialcoll`.
The first program `bin/sha1dcsum` will detect and warn for files that were generated with a cryptanalytic SHA-1 collision attack like the one documented at https://shattered.io/.
The second program `bin/sha1dcsum_partialcoll` will detect and warn for files that were generated with a cryptanalytic collision attack against reduced-round SHA-1 (of which there are a few examples so far).

Examples:
```
bin/sha1dcsum test/sha1_reducedsha_coll.bin test/shattered-1.pdf
bin/sha1dcsum_partialcoll test/sha1reducedsha_coll.bin test/shattered-1.pdf
pipe_data | bin/sha1dcsum -
```

## Library usage

See the documentation in `lib/sha1.h`. Here is a simple example code snippet:
```
#include <sha1dc/sha1.h>

SHA1_CTX ctx;
unsigned char hash[20];
SHA1DCInit(&ctx);

/** disable safe-hash mode (safe-hash mode is enabled by default) **/
// SHA1DCSetSafeHash(&ctx, 0);
/** disable use of unavoidable attack conditions to speed up detection (enabled by default) **/
// SHA1DCSetUseUBC(&ctx, 0); 

SHA1DCUpdate(&ctx, buffer, (unsigned)(size));

int iscoll = SHA1DCFinal(hash,&ctx);
if (iscoll)
    printf("collision detected");
else
    printf("no collision detected");
```

## Inclusion in other programs

In order to make it easier to include these sources in other project
there are several preprocessor macros that the code uses. Rather than
copy/pasting and customizing or specializing the code, first see if
setting any of these defines appropriately will allow you to avoid
modifying the code yourself.

- SHA1DC_NO_STANDARD_INCLUDES

 Skips including standard headers. Use this if your project for
 whatever reason wishes to do its own header includes.

- SHA1DC_CUSTOM_INCLUDE_SHA1_C

  Includes a custom header at the top of sha1.c. Usually this would be
  set in conjunction with SHA1DC_NO_STANDARD_INCLUDES to point to a
  header file which includes various standard headers.

- SHA1DC_INIT_SAFE_HASH_DEFAULT

  Sets the default for safe_hash in SHA1DCInit(). Valid values are 0
  and 1. If unset 1 is the default.

- SHA1DC_CUSTOM_TRAILING_INCLUDE_SHA1_C

  Includes a custom trailer in sha1.c. Useful for any extra utility
  functions that make use of the functions already defined in sha1.c.

- SHA1DC_CUSTOM_TRAILING_INCLUDE_SHA1_H

  Includes a custom trailer in sha1.h. Useful for defining the
  prototypes of the functions or code included by
  SHA1DC_CUSTOM_TRAILING_INCLUDE_SHA1_C.

- SHA1DC_CUSTOM_INCLUDE_UBC_CHECK_C

  Includes a custom header at the top of ubc_check.c.

- SHA1DC_CUSTOM_TRAILING_INCLUDE_UBC_CHECK_C

  Includes a custom trailer in ubc_check.c.

- SHA1DC_CUSTOM_TRAILING_INCLUDE_UBC_CHECK_H

  Includes a custom trailer in ubc_check.H.

This code will try to auto-detect certain things based on
CPU/platform. Unless you're running on some really obscure CPU or
porting to a new platform you should not need to tweak this. If you do
please open an issue at
https://github.com/cr-marcstevens/sha1collisiondetection

- SHA1DC_FORCE_LITTLEENDIAN / SHA1DC_FORCE_BIGENDIAN

  Override the check for processor endianenss and force either
  Little-Endian or Big-Endian.

- SHA1DC_FORCE_UNALIGNED_ACCESS

  Permit unaligned access. This will fail on e.g. SPARC processors, so
  it's only permitted on a whitelist of processors. If your CPU isn't
  detected as allowing this, and allows unaligned access, setting this
  may improve performance (or make it worse, if the kernel has to
  catch and emulate such access on its own).
