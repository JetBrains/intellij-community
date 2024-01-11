/*
 * Copyright 2015 Higher Frequency Trading http://www.higherfrequencytrading.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;

/**
 * Adapted version of XXH3 implementation from https://github.com/Cyan4973/xxHash.
 * This implementation provides endian-independent hash values, but it's slower on big-endian platforms.
 */
@SuppressWarnings({"DuplicatedCode", "CommentedOutCode"})
@ApiStatus.Internal
public final class Xxh3Impl {
  private Xxh3Impl() {
  }

  // Pseudorandom secret taken directly from FARSH
  //private static final byte[] secret = {
  //  (byte)0xb8, (byte)0xfe, (byte)0x6c, (byte)0x39, (byte)0x23, (byte)0xa4, (byte)0x4b, (byte)0xbe, (byte)0x7c, (byte)0x01, (byte)0x81,
  //  (byte)0x2c, (byte)0xf7, (byte)0x21, (byte)0xad, (byte)0x1c,
  //  (byte)0xde, (byte)0xd4, (byte)0x6d, (byte)0xe9, (byte)0x83, (byte)0x90, (byte)0x97, (byte)0xdb, (byte)0x72, (byte)0x40, (byte)0xa4,
  //  (byte)0xa4, (byte)0xb7, (byte)0xb3, (byte)0x67, (byte)0x1f,
  //  (byte)0xcb, (byte)0x79, (byte)0xe6, (byte)0x4e, (byte)0xcc, (byte)0xc0, (byte)0xe5, (byte)0x78, (byte)0x82, (byte)0x5a, (byte)0xd0,
  //  (byte)0x7d, (byte)0xcc, (byte)0xff, (byte)0x72, (byte)0x21,
  //  (byte)0xb8, (byte)0x08, (byte)0x46, (byte)0x74, (byte)0xf7, (byte)0x43, (byte)0x24, (byte)0x8e, (byte)0xe0, (byte)0x35, (byte)0x90,
  //  (byte)0xe6, (byte)0x81, (byte)0x3a, (byte)0x26, (byte)0x4c,
  //  (byte)0x3c, (byte)0x28, (byte)0x52, (byte)0xbb, (byte)0x91, (byte)0xc3, (byte)0x00, (byte)0xcb, (byte)0x88, (byte)0xd0, (byte)0x65,
  //  (byte)0x8b, (byte)0x1b, (byte)0x53, (byte)0x2e, (byte)0xa3,
  //  (byte)0x71, (byte)0x64, (byte)0x48, (byte)0x97, (byte)0xa2, (byte)0x0d, (byte)0xf9, (byte)0x4e, (byte)0x38, (byte)0x19, (byte)0xef,
  //  (byte)0x46, (byte)0xa9, (byte)0xde, (byte)0xac, (byte)0xd8,
  //  (byte)0xa8, (byte)0xfa, (byte)0x76, (byte)0x3f, (byte)0xe3, (byte)0x9c, (byte)0x34, (byte)0x3f, (byte)0xf9, (byte)0xdc, (byte)0xbb,
  //  (byte)0xc7, (byte)0xc7, (byte)0x0b, (byte)0x4f, (byte)0x1d,
  //  (byte)0x8a, (byte)0x51, (byte)0xe0, (byte)0x4b, (byte)0xcd, (byte)0xb4, (byte)0x59, (byte)0x31, (byte)0xc8, (byte)0x9f, (byte)0x7e,
  //  (byte)0xc9, (byte)0xd9, (byte)0x78, (byte)0x73, (byte)0x64,
  //  (byte)0xea, (byte)0xc5, (byte)0xac, (byte)0x83, (byte)0x34, (byte)0xd3, (byte)0xeb, (byte)0xc3, (byte)0xc5, (byte)0x81, (byte)0xa0,
  //  (byte)0xff, (byte)0xfa, (byte)0x13, (byte)0x63, (byte)0xeb,
  //  (byte)0x17, (byte)0x0d, (byte)0xdd, (byte)0x51, (byte)0xb7, (byte)0xf0, (byte)0xda, (byte)0x49, (byte)0xd3, (byte)0x16, (byte)0x55,
  //  (byte)0x26, (byte)0x29, (byte)0xd4, (byte)0x68, (byte)0x9e,
  //  (byte)0x2b, (byte)0x16, (byte)0xbe, (byte)0x58, (byte)0x7d, (byte)0x47, (byte)0xa1, (byte)0xfc, (byte)0x8f, (byte)0xf8, (byte)0xb8,
  //  (byte)0xd1, (byte)0x7a, (byte)0xd0, (byte)0x31, (byte)0xce,
  //  (byte)0x45, (byte)0xcb, (byte)0x3a, (byte)0x8f, (byte)0x95, (byte)0x16, (byte)0x04, (byte)0x28, (byte)0xaf, (byte)0xd7, (byte)0xfb,
  //  (byte)0xca, (byte)0xbb, (byte)0x4b, (byte)0x40, (byte)0x7e,
  //};

  private static final long[] secretLong = {
    -4734510112055689544L, 8988705074615774462L, 107169723235645804L, -9150895811085458631L, 3206846044944704547L, -635991603978286172L,
    2447473855086509643L, -5971219860401587010L, 2066345149520216444L, -2441886536549236479L, -3108015162914296703L, 7914194659941938988L,
    -1626409839981944329L, -8941494824140493535L, -8033320652366799699L, -7525369938742813156L, -2623469361688619810L, 8276375387167616468L,
    4644015609783511405L, -6611157965513653271L, -6583065893254229885L, -5213861871876335728L, -5496743794819540073L, 7472518124495991515L,
    2262974939099578482L, -3810212738154322880L, 8776142829118792868L, -1839215637059881052L, 5685365492914041783L, -3724786431015557197L,
    -4554178371385614489L, -1891287204249351393L, 8711581037947681227L, -9045227235349436807L, 6521908138563358438L, -3433288310154277810L,
    9065845566317379788L, -3711581430728825408L, -14498364963784475L, 8286566680123572856L, 2410270004345854594L, -5178731653526335398L,
    628288925819764176L, 5046485836271438973L, 8378393743697575884L, -615790245780032769L, 4897510963931521394L, 2613204287568263201L,
    -8204357891075471176L, -2265833688187779576L, 3882259165203625030L, -8055285457383852172L, -1832905809766104073L, -9086416637098318781L,
    4215904233249082916L, 2754656949352390798L, 5487137525590930912L, 4344889773235015733L, 2899275987193816720L, 5920048007935066598L,
    -4948848801086031231L, -7945666784801315270L, -4354493403153806298L, 55047854181858380L, -3818837453329782724L, -8589771024315493848L,
    -3420260712846345390L, 7336514198459093435L, -8402080243849837679L, 1984792007109443779L, 5988533398925789952L, 3338042034334238923L,
    -6688317018830679928L, 8188439481968977360L, 7237745495519234917L, 5216419214072683403L, -7545670736427461861L, -6730831521841467821L,
    982514005898797870L, -500565212929953373L, 5690594596133299313L, 4057454151265110116L, 1817289281226577736L, -1217880312389983593L,
    5111331831722610082L, -6249044541332063987L, -2402310933491200263L, -5990164332231968690L, -2833645246901970632L, -6280079608045441255L,
    -384819531158567185L, 8573350489219836230L, 4573118074737974953L, -2071806484620464930L, -7141794803835414356L, 3791154848057698520L,
    4554437623014685352L, -486612386300594438L, -2523916620961464458L, -4909775443879730369L, -4054404076451619613L, -4051062782047603556L,
    848866664462761780L, 5695865814404364607L, 2111919702937427193L, -8494546410135897124L, 5875540889195497403L, -2282891677615274041L,
    5467459601266838471L, -3653580031866876149L, -5418591349844075185L, 6464017090953185821L, 3556072174620004746L, -4021334359191855023L,
    -6933237364981675040L, 9124231484359888203L, -3927526142850255667L, -2753530472436770380L, 8708212900181324121L, 8320639771003045937L,
    7238261902898274248L, -1556992608276218209L, -4185422456575899266L, -5997129611619018295L, -8958567948248450855L, 3784058077962335096L,
    -3227810254839716749L, -1453760514566526364L, -4329134394285701654L, -4196251135427498811L, -9095648454776683604L,
    -6881001310379625341L, -26878911368670412L, -360392965937173549L, 1439744095735366635L, 7139325810128831939L, -1485321483350670907L,
    1723580219865931905L, 943481457726914464L, -2518330316883232001L, 5898885483309765626L, -5237161843349560557L, -1101321574019503261L,
    -2670433016801847317L, 5321830579834785047L, -3221803331004277491L, 1644739493610607069L, 6131320256870790993L, 2762139043194663095L,
    2965150961192524528L, -3158951516726670886L, 7553707719620219721L, -7032137544937171245L, 3143064850383918358L, 1597544665906226773L,
    -4749560797652047578L, 6394572897509757993L, 9032178055121889492L, 5151371122220703336L, -6825348890156979298L, -242834301215959509L,
    -8071399103737053674L, -535932061014468418L, -5118182661306221224L, -3334642226765412483L, 8850058120466833735L, -3424193974287467359L,
    3589503944184336380L, -3588858202114426737L, 5030012605302946040L, -3799403997270715976L, 4236556626373409489L, -8125959076964085638L,
    -7669846995664752176L, 1627364323045527089L, 294587268038608334L, 2883454493032893253L, -5825401622958753077L, -2905059236606800070L,
    -299578263794707057L, -3820222711603128683L, -4914839139546299370L, 5457178556493670404L, 4633003122163691304L, 9097354517224871855L
  };
  private static final int[] secretInt = {
    963444408, 590966014, -1541195412, 1269048121, -1102339037, 2092845988, 24952395, -2130608962, 746652028, -148078335, 569846913,
    -1390282964, 481108471, -568546015, -723641171, 1842667036, -378678050, -2081853996, -1870403219, -1752136727, -610824061, 1926993808,
    1081269143, -1539280165, -1532739470, -1213946816, -1279810396, 1739831204, 526889911, -887134285, 2043354983, -428225761, 1323727307,
    -867244423, -1060352282, -440349618, 2028323020, -2106006080, 1518500069, -799374728, 2110806658, -864169894, -3375664, 1929366653,
    561184716, -1205767425, 146284914, 1174976545, 1950746808, -143374840, 1140290630, 608434036, -1910225929, -527555517, 903908900,
    -1875517298, -426756640, -2115596235, 981591696, 641368550, 1277573761, 1011623482, 675040294, 1378368588, -1152243652, -1849994712,
    -1013859502, 12816827, -889142383, -1999961917, -796341504, 1708165323, -1956261752, 462120400, 1394314085, 777198475, -1557245157,
    1906519635, 1685168942, 1214542243, -1756863375, -1567143836, 228759368, -116546921, 1324944802, 944699661, 423120633, -283559858,
    1190074680, -1454969063, -559331601, -1394693818, -659759447, -1462194978, -89597780, 1996138712, 1064762024, -482380038, -1662828682,
    882697023, 1060412643, -113298276, -587645132, -1143146177, -943989511, -943211556, 197642171, 1326172103, 491719623, -1977790709,
    1368005967, -531527139, 1272992138, -850665391, -1261614112, 1505021259, 827962573, -936289868, -1614270119, 2124400689, -914448440,
    -641106273, 2027538814, 1937299913, 1685289177, -362515592, -974494605, -1396315548, -2085829142, 881044677, -751533140, -338479997,
    -1007955148, -977015853, -2117745685, -1602107965, -6258235, -83910527, 335216544, 1662253823, -345828358, 401302291, 219671395,
    -586344469, 1373441303, -1219371763, -256421411, -621758639, 1239085239, -750134544, 382945754, 1427559241, 643110611, 690378006,
    -735500715, 1758734630, -1637297111, 731801812, 371957352, -1105843298, 1488852523, 2102967830, 1199397054, -1589150376, -56539267,
    -1879269049, -124781407, -1191669764, -776406897, 2060564728, -797257288, 835746513, -835596166, 1171141072, -884617679, 986400206,
    -1891972283, -1785775413, 378900282, 68588943, 671356565, -1356332010, -676386812, -69751000, -889464913, -1144325161, 1270598395,
    1078705098, 2118142907
  };

  //static {
  //  // cannot use ByteArrayAccess in this module
  //  ByteBuffer buffer = ByteBuffer.wrap(secret).order(ByteOrder.LITTLE_ENDIAN);
  //  secretLong = new long[(secret.length - Long.BYTES) + 1];
  //  for (int i = 0, n = secretLong.length; i < n; i++) {
  //    secretLong[i] = buffer.getLong(i);
  //  }
  //
  //  secretInt = new int[(secret.length - Integer.BYTES) + 1];
  //  for (int i = 0, n = secretInt.length; i < n; i++) {
  //    secretInt[i] = buffer.getInt(i);
  //  }
  //}

  // Primes
  private static final long XXH_PRIME32_1 = 0x9E3779B1L;   /*!< 0b10011110001101110111100110110001 */
  private static final long XXH_PRIME32_2 = 0x85EBCA77L;   /*!< 0b10000101111010111100101001110111 */
  private static final long XXH_PRIME32_3 = 0xC2B2AE3DL;   /*!< 0b11000010101100101010111000111101 */

  private static final long XXH_PRIME64_1 =
    0x9E3779B185EBCA87L;   /*!< 0b1001111000110111011110011011000110000101111010111100101010000111 */
  private static final long XXH_PRIME64_2 =
    0xC2B2AE3D27D4EB4FL;   /*!< 0b1100001010110010101011100011110100100111110101001110101101001111 */
  private static final long XXH_PRIME64_3 =
    0x165667B19E3779F9L;   /*!< 0b0001011001010110011001111011000110011110001101110111100111111001 */
  private static final long XXH_PRIME64_4 =
    0x85EBCA77C2B2AE63L;   /*!< 0b1000010111101011110010100111011111000010101100101010111001100011 */
  private static final long XXH_PRIME64_5 =
    0x27D4EB2F165667C5L;   /*!< 0b0010011111010100111010110010111100010110010101100110011111000101 */

  // only support fixed size secret
  private static final int nbStripesPerBlock = (192 - 64) / 8;
  private static final int block_len = 64 * nbStripesPerBlock;

  static int getBlockLength() {
    return block_len;
  }

  public static <T> long hash(final T input, final Access<T> access, final int off, final int length, final long seed) {
    if (length <= 16) {
      // len_0to16_64b
      if (length > 8) {
        // len_9to16_64b
        final long bitflip1 = secretLong[24] ^ secretLong[32] + seed;
        final long bitflip2 = secretLong[40] ^ secretLong[48] - seed;
        final long input_lo = access.i64(input, off) ^ bitflip1;
        final long input_hi = access.i64(input, off + length - 8) ^ bitflip2;
        final long acc = length + Long.reverseBytes(input_lo) + input_hi + unsignedLongMulXorFold(input_lo, input_hi);
        return avalanche(acc);
      }
      if (length >= 4) {
        // len_4to8_64b
        long s = seed ^ Long.reverseBytes(seed & 0xFFFFFFFFL);
        final long input1 = access.i32(input, off); // high int will be shifted
        final long input2 = access.u32(input, off + length - 4);
        final long bitflip = (secretLong[8] ^ secretLong[16]) - s;
        final long keyed = (input2 + (input1 << 32)) ^ bitflip;
        return rrmxmx(keyed, length);
      }
      if (length != 0) {
        // len_1to3_64b
        final int c1 = access.i8(input, off) & 0xff;
        final int c2 = access.i8(input, off + (length >> 1)); // high 3 bytes will be shifted
        final int c3 = access.i8(input, off + length - 1) & 0xff;
        final long combined = unsignedInt((c1 << 16) | (c2 << 24) | c3 | (length << 8));
        final long bitflip = unsignedInt(secretInt[0] ^ secretInt[4]) + seed;
        return XXH64_avalanche(combined ^ bitflip);
      }
      return XXH64_avalanche(seed ^ secretLong[56] ^ secretLong[64]);
    }
    if (length <= 128) {
      // len_17to128_64b
      long acc = length * XXH_PRIME64_1;

      if (length > 32) {
        if (length > 64) {
          if (length > 96) {
            acc += mix16B(seed, input, access, off + 48, 96);
            acc += mix16B(seed, input, access, off + length - 64, 112);
          }
          acc += mix16B(seed, input, access, off + 32, 64);
          acc += mix16B(seed, input, access, off + length - 48, 80);
        }
        acc += mix16B(seed, input, access, off + 16, 32);
        acc += mix16B(seed, input, access, off + length - 32, 48);
      }
      acc += mix16B(seed, input, access, off, 0);
      acc += mix16B(seed, input, access, off + length - 16, 16);

      return avalanche(acc);
    }
    return hashLarge(input, access, off, length, seed);
  }

  // make JIT inline more happy - extract to separate method
  private static <T> long hashLarge(T input, Access<T> access, int off, int length, long seed) {
    if (length <= 240) {
      // len_129to240_64b
      long acc = length * XXH_PRIME64_1;
      final int nbRounds = length / 16;
      int i = 0;
      for (; i < 8; ++i) {
        acc += mix16B(seed, input, access, off + 16 * i, 16 * i);
      }
      acc = avalanche(acc);

      for (; i < nbRounds; ++i) {
        acc += mix16B(seed, input, access, off + 16 * i, 16 * (i - 8) + 3);
      }

      /* last bytes */
      acc += mix16B(seed, input, access, off + length - 16, 136 - 17);
      return avalanche(acc);
    }

    // hashLong_64b_internal
    long acc_0 = XXH_PRIME32_3;
    long acc_1 = XXH_PRIME64_1;
    long acc_2 = XXH_PRIME64_2;
    long acc_3 = XXH_PRIME64_3;
    long acc_4 = XXH_PRIME64_4;
    long acc_5 = XXH_PRIME32_2;
    long acc_6 = XXH_PRIME64_5;
    long acc_7 = XXH_PRIME32_1;

    // hashLong_internal_loop
    final int nb_blocks = (length - 1) / block_len;
    for (int n = 0; n < nb_blocks; n++) {
      // accumulate
      final int offBlock = off + n * block_len;
      for (int s = 0; s < nbStripesPerBlock; s++) {
        // accumulate_512
        final int offStripe = offBlock + s * 64;
        final int offSec = s * 8;
        {
          final long data_val_0 = access.i64(input, offStripe);
          final long data_val_1 = access.i64(input, offStripe + 8);
          final long data_key_0 = data_val_0 ^ secretLong[offSec];
          final long data_key_1 = data_val_1 ^ secretLong[offSec + 8];
          /* swap adjacent lanes */
          acc_0 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
          acc_1 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
        }
        {
          final long data_val_0 = access.i64(input, offStripe + 8 * 2);
          final long data_val_1 = access.i64(input, offStripe + 8 * 3);
          final long data_key_0 = data_val_0 ^ secretLong[offSec + 8 * 2];
          final long data_key_1 = data_val_1 ^ secretLong[offSec + 8 * 3];
          /* swap adjacent lanes */
          acc_2 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
          acc_3 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
        }
        {
          final long data_val_0 = access.i64(input, offStripe + 8 * 4);
          final long data_val_1 = access.i64(input, offStripe + 8 * 5);
          final long data_key_0 = data_val_0 ^ secretLong[offSec + 8 * 4];
          final long data_key_1 = data_val_1 ^ secretLong[offSec + 8 * 5];
          /* swap adjacent lanes */
          acc_4 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
          acc_5 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
        }
        {
          final long data_val_0 = access.i64(input, offStripe + 8 * 6);
          final long data_val_1 = access.i64(input, offStripe + 8 * 7);
          final long data_key_0 = data_val_0 ^ secretLong[offSec + 8 * 6];
          final long data_key_1 = data_val_1 ^ secretLong[offSec + 8 * 7];
          /* swap adjacent lanes */
          acc_6 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
          acc_7 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
        }
      }

      // scrambleAcc_scalar
      final int offSec = 192 - 64;
      acc_0 = (acc_0 ^ (acc_0 >>> 47) ^ secretLong[offSec]) * XXH_PRIME32_1;
      acc_1 = (acc_1 ^ (acc_1 >>> 47) ^ secretLong[offSec + 8]) * XXH_PRIME32_1;
      acc_2 = (acc_2 ^ (acc_2 >>> 47) ^ secretLong[offSec + 8 * 2]) * XXH_PRIME32_1;
      acc_3 = (acc_3 ^ (acc_3 >>> 47) ^ secretLong[offSec + 8 * 3]) * XXH_PRIME32_1;
      acc_4 = (acc_4 ^ (acc_4 >>> 47) ^ secretLong[offSec + 8 * 4]) * XXH_PRIME32_1;
      acc_5 = (acc_5 ^ (acc_5 >>> 47) ^ secretLong[offSec + 8 * 5]) * XXH_PRIME32_1;
      acc_6 = (acc_6 ^ (acc_6 >>> 47) ^ secretLong[offSec + 8 * 6]) * XXH_PRIME32_1;
      acc_7 = (acc_7 ^ (acc_7 >>> 47) ^ secretLong[offSec + 8 * 7]) * XXH_PRIME32_1;
    }

    /* last partial block */
    final long nbStripes = ((length - 1) - (block_len * nb_blocks)) / 64;
    final int offBlock = off + block_len * nb_blocks;
    for (int s = 0; s < nbStripes; s++) {
      // accumulate_512
      final int offStripe = offBlock + s * 64;
      final int offSec = s * 8;
      {
        final long data_val_0 = access.i64(input, offStripe);
        final long data_val_1 = access.i64(input, offStripe + 8);
        final long data_key_0 = data_val_0 ^ secretLong[offSec];
        final long data_key_1 = data_val_1 ^ secretLong[offSec + 8];
        /* swap adjacent lanes */
        acc_0 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
        acc_1 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
      }
      {
        final long data_val_0 = access.i64(input, offStripe + 8 * 2);
        final long data_val_1 = access.i64(input, offStripe + 8 * 3);
        final long data_key_0 = data_val_0 ^ secretLong[offSec + 8 * 2];
        final long data_key_1 = data_val_1 ^ secretLong[offSec + 8 * 3];
        /* swap adjacent lanes */
        acc_2 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
        acc_3 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
      }
      {
        final long data_val_0 = access.i64(input, offStripe + 8 * 4);
        final long data_val_1 = access.i64(input, offStripe + 8 * 5);
        final long data_key_0 = data_val_0 ^ secretLong[offSec + 8 * 4];
        final long data_key_1 = data_val_1 ^ secretLong[offSec + 8 * 5];
        /* swap adjacent lanes */
        acc_4 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
        acc_5 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
      }
      {
        final long data_val_0 = access.i64(input, offStripe + 8 * 6);
        final long data_val_1 = access.i64(input, offStripe + 8 * 7);
        final long data_key_0 = data_val_0 ^ secretLong[offSec + 8 * 6];
        final long data_key_1 = data_val_1 ^ secretLong[offSec + 8 * 7];
        /* swap adjacent lanes */
        acc_6 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
        acc_7 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
      }
    }

    /* last stripe */
    // accumulate_512
    final int offStripe = off + length - 64;
    final int offSec = 192 - 64 - 7;
    {
      final long data_val_0 = access.i64(input, offStripe);
      final long data_val_1 = access.i64(input, offStripe + 8);
      final long data_key_0 = data_val_0 ^ secretLong[offSec];
      final long data_key_1 = data_val_1 ^ secretLong[offSec + 8];
      /* swap adjacent lanes */
      acc_0 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
      acc_1 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
    }
    {
      final long data_val_0 = access.i64(input, offStripe + 8 * 2);
      final long data_val_1 = access.i64(input, offStripe + 8 * 3);
      final long data_key_0 = data_val_0 ^ secretLong[offSec + 8 * 2];
      final long data_key_1 = data_val_1 ^ secretLong[offSec + 8 * 3];
      /* swap adjacent lanes */
      acc_2 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
      acc_3 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
    }
    {
      final long data_val_0 = access.i64(input, offStripe + 8 * 4);
      final long data_val_1 = access.i64(input, offStripe + 8 * 5);
      final long data_key_0 = data_val_0 ^ secretLong[offSec + 8 * 4];
      final long data_key_1 = data_val_1 ^ secretLong[offSec + 8 * 5];
      /* swap adjacent lanes */
      acc_4 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
      acc_5 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
    }
    {
      final long data_val_0 = access.i64(input, offStripe + 8 * 6);
      final long data_val_1 = access.i64(input, offStripe + 8 * 7);
      final long data_key_0 = data_val_0 ^ secretLong[offSec + 8 * 6];
      final long data_key_1 = data_val_1 ^ secretLong[offSec + 8 * 7];
      /* swap adjacent lanes */
      acc_6 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
      acc_7 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
    }

    // merge accumulators
    final long result64 = length * XXH_PRIME64_1
                          + mix2Accs(acc_0, acc_1, 11)
                          + mix2Accs(acc_2, acc_3, 11 + 16)
                          + mix2Accs(acc_4, acc_5, 11 + 16 * 2)
                          + mix2Accs(acc_6, acc_7, 11 + 16 * 3);

    return avalanche(result64);
  }

  private static long XXH64_avalanche(long h64) {
    h64 ^= h64 >>> 33;
    h64 *= XXH_PRIME64_2;
    h64 ^= h64 >>> 29;
    h64 *= XXH_PRIME64_3;
    return h64 ^ (h64 >>> 32);
  }

  private static long avalanche(long h64) {
    h64 ^= h64 >>> 37;
    h64 *= 0x165667919E3779F9L;
    return h64 ^ (h64 >>> 32);
  }

  @SuppressWarnings("SpellCheckingInspection")
  static long rrmxmx(long h64, final long length) {
    h64 ^= Long.rotateLeft(h64, 49) ^ Long.rotateLeft(h64, 24);
    h64 *= 0x9FB21C651E98DF25L;
    h64 ^= (h64 >>> 35) + length;
    h64 *= 0x9FB21C651E98DF25L;
    return h64 ^ (h64 >>> 28);
  }

  private static <T> long mix16B(long seed, final T input, final Access<T> access, final int offIn, final int offSec) {
    final long input_lo = access.i64(input, offIn);
    final long input_hi = access.i64(input, offIn + 8);
    return unsignedLongMulXorFold(
      input_lo ^ (secretLong[offSec] + seed),
      input_hi ^ (secretLong[offSec + 8] - seed)
    );
  }

  @SuppressWarnings("SpellCheckingInspection")
  private static long mix2Accs(final long acc_lh, final long acc_rh, final int offSec) {
    return unsignedLongMulXorFold(
      acc_lh ^ secretLong[offSec],
      acc_rh ^ secretLong[offSec + 8]
    );
  }

  private static long unsignedLongMulXorFold(final long lhs, final long rhs) {
    final long upper = multiplyHigh(lhs, rhs) + ((lhs >> 63) & rhs) + ((rhs >> 63) & lhs);
    final long lower = lhs * rhs;
    return lower ^ upper;
  }

  private static long unsignedInt(int i) {
    return i & 0xFFFFFFFFL;
  }

  // from JDK - only 9+
  private static long multiplyHigh(long x, long y) {
    if (x < 0 || y < 0) {
      // Use technique from section 8-2 of Henry S. Warren, Jr.,
      // Hacker's Delight (2nd ed.) (Addison Wesley, 2013), 173-174.
      long x1 = x >> 32;
      long x2 = x & 0xFFFFFFFFL;
      long y1 = y >> 32;
      long y2 = y & 0xFFFFFFFFL;
      long z2 = x2 * y2;
      long t = x1 * y2 + (z2 >>> 32);
      long z1 = t & 0xFFFFFFFFL;
      long z0 = t >> 32;
      z1 += x2 * y1;
      return x1 * y1 + z0 + (z1 >> 32);
    }
    else {
      // Use Karatsuba technique with two base 2^32 digits.
      long x1 = x >>> 32;
      long y1 = y >>> 32;
      long x2 = x & 0xFFFFFFFFL;
      long y2 = y & 0xFFFFFFFFL;
      long A = x1 * y1;
      long B = x2 * y2;
      long C = (x1 + x2) * (y1 + y2);
      long K = C - A - B;
      return (((B >>> 32) + K) >>> 32) + A;
    }
  }
}