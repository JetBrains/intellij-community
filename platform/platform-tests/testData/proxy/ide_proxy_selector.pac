function FindProxyForURL(url, host) {
  if (shExpMatch(host, "example.com"))
   return "PROXY proxy.domain.com:3129; DIRECT";
  if (shExpMatch(host, "other.example.com"))
   return "PROXY p1.domain.com:3129; SOCKS p2.domain.com:9999; DIRECT";
  if (shExpMatch(url, "https://*.example-domain.com/"))
   return "PROXY p1.domain.com:3129";
  return "DIRECT";
}