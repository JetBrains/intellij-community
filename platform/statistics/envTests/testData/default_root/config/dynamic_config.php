<?php

$time = time();

$config = <<<CONFIG
{
  "productCode": "IC",
  "versions": [
    {
      "majorBuildVersionBorders": {
        "from": "193.1"
      },
      "releaseFilters": [
        {
          "releaseType": "ALL",
          "from": 0,
          "to": 256
        }
      ],
      "endpoints": {
        "send": "http://test-send-url",
        "metadata": "metadata/$time/"
      }
    }
  ]
}
CONFIG;

echo $config;