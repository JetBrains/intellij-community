import abc
from typing import List, Type, Optional
from data_processing.datapoint import CodeDataPoint
from data_processing.processors.abstract_processor import ProcessorBase
class MetaInfoComposerBase(ProcessorBase):⇥def __init__(self, lang_sep_symbol: str, meta_info_sep_symbol: str):⇥self._lang_sep_symbol = lang_sep_symbol
self._meta_info_sep_symbol = meta_info_sep_symbol⇤@staticmethod
def _compose(dp: CodeDataPoint, lang_sep_char: str, meta_info_sep_char: str) -> CodeDataPoint:⇥dp.metainfo = f"{dp.extension}{lang_sep_char}{dp.filepath}{meta_info_sep_char}"
return dp⇤@abc.abstractmethod
def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:⇥raise NotImplementedError⇤@staticmethod
def is_deterministic() -> bool:⇥return True⇤@staticmethod
def is_slow() -> bool:⇥return False⇤@staticmethod
def must_be_after() -> List[Type[ProcessorBase]]:⇥return []⇤@staticmethod
def must_be_before() -> List[Type[ProcessorBase]]:⇥return []⇤⇤class MetaInfoComposerTrainData(MetaInfoComposerBase):⇥def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:⇥return self._compose(dp, f"\n{self._lang_sep_symbol}\n", f"\n{self._meta_info_sep_symbol}\n")⇤⇤class MetaInfoComposerTrainModel(MetaInfoComposerBase):⇥def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:⇥return self._compose(dp, self._lang_sep_symbol, self._meta_info_sep_symbol)⇤⇤class MetaInfoComposerInference(MetaInfoComposerBase):⇥def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:⇥return self._compose(dp, self._lang_sep_symbol, self._meta_info_sep_symbol)⇤⇤